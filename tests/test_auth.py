"""
Tests for api/auth.py — P0.6 OAuth state validation and cookie hardening.

Covers:
  ✓ GET /api/auth/login sets a state cookie in the redirect response
  ✓ GET /api/auth/login includes state param in the Discord authorize URL
  ✓ GET /api/auth/callback succeeds when state matches the cookie
  ✓ GET /api/auth/callback rejects missing state parameter (400)
  ✓ GET /api/auth/callback rejects mismatched state (400)
  ✓ GET /api/auth/callback rejects missing state cookie (400)
  ✓ GET /api/auth/callback clears the state cookie on success
  ✓ Successful callback sets JWT cookie with HttpOnly + SameSite=Lax
  ✓ JWT cookie has Secure flag when LOCAL_DEV is not set
  ✓ JWT cookie does NOT have Secure flag when LOCAL_DEV=true
  ✓ State cookie has Secure flag when LOCAL_DEV is not set
  ✓ _is_secure_cookie() returns False only for LOCAL_DEV=true/1/yes
"""
from __future__ import annotations

import json
import os
from datetime import datetime, timedelta, timezone
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi.testclient import TestClient
from jose import jwt as jose_jwt

# ---------------------------------------------------------------------------
# App fixture
# ---------------------------------------------------------------------------

# Minimal env so auth.py can import without RuntimeError
_BASE_ENV = {
    "DISCORD_CLIENT_ID": "test_client_id",
    "DISCORD_CLIENT_SECRET": "test_client_secret",
    "DISCORD_REDIRECT_URI": "http://localhost:8080/api/auth/callback",
    "JWT_SECRET": "test_jwt_secret_for_testing_only_32_chars_ok",
}


@pytest.fixture(autouse=True)
def _set_env(monkeypatch):
    for key, val in _BASE_ENV.items():
        monkeypatch.setenv(key, val)
    monkeypatch.delenv("LOCAL_DEV", raising=False)


@pytest.fixture()
def client():
    from fastapi import FastAPI
    from api.auth import router
    app = FastAPI()
    app.include_router(router)
    return TestClient(app, follow_redirects=False)


# ---------------------------------------------------------------------------
# Helper — build a Discord user response mock
# ---------------------------------------------------------------------------

def _discord_token_response(access_token: str = "discord_access_token") -> dict:
    return {"access_token": access_token, "token_type": "Bearer"}


def _discord_user_response(discord_id: str = "123456789") -> dict:
    return {"id": discord_id, "username": "testuser", "avatar": None}


# ---------------------------------------------------------------------------
# /login tests
# ---------------------------------------------------------------------------

class TestLogin:
    def test_redirects_to_discord(self, client):
        resp = client.get("/api/auth/login")
        assert resp.status_code in (302, 307)
        assert "discord.com/oauth2/authorize" in resp.headers["location"]

    def test_login_includes_state_in_url(self, client):
        resp = client.get("/api/auth/login")
        location = resp.headers["location"]
        assert "state=" in location

    def test_login_sets_state_cookie(self, client):
        resp = client.get("/api/auth/login")
        assert "mindforge_oauth_state" in resp.cookies

    def test_state_cookie_is_httponly(self, client):
        resp = client.get("/api/auth/login")
        # TestClient stores Set-Cookie headers — check raw header for httponly
        set_cookie = resp.headers.get("set-cookie", "")
        assert "httponly" in set_cookie.lower()

    def test_state_cookie_secure_by_default(self, client):
        resp = client.get("/api/auth/login")
        set_cookie = resp.headers.get("set-cookie", "")
        assert "secure" in set_cookie.lower()

    def test_state_cookie_not_secure_when_local_dev(self, client, monkeypatch):
        monkeypatch.setenv("LOCAL_DEV", "true")
        # Reload the is_secure helper to pick up new env
        import importlib
        import api.auth as auth_module
        with patch.object(auth_module, "_is_secure_cookie", return_value=False):
            resp = client.get("/api/auth/login")
        set_cookie = resp.headers.get("set-cookie", "")
        assert "secure" not in set_cookie.lower()


# ---------------------------------------------------------------------------
# /callback tests
# ---------------------------------------------------------------------------

class TestCallback:
    def _mock_httpx(self, discord_id: str = "123456789"):
        """Return an async context manager mock that simulates Discord API responses."""
        token_resp = MagicMock()
        token_resp.status_code = 200
        token_resp.json.return_value = _discord_token_response()

        user_resp = MagicMock()
        user_resp.status_code = 200
        user_resp.json.return_value = _discord_user_response(discord_id)

        async_client = AsyncMock()
        async_client.__aenter__ = AsyncMock(return_value=async_client)
        async_client.__aexit__ = AsyncMock(return_value=False)
        async_client.post = AsyncMock(return_value=token_resp)
        async_client.get = AsyncMock(return_value=user_resp)

        return async_client

    def test_valid_state_succeeds(self, client):
        # Get a state value from login
        login_resp = client.get("/api/auth/login")
        state = login_resp.cookies["mindforge_oauth_state"]

        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code", "state": state},
                cookies={"mindforge_oauth_state": state},
            )
        assert resp.status_code in (302, 307)

    def test_missing_state_param_rejected(self, client):
        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code"},
                cookies={"mindforge_oauth_state": "some_state"},
            )
        assert resp.status_code == 400

    def test_mismatched_state_rejected(self, client):
        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code", "state": "wrong_state"},
                cookies={"mindforge_oauth_state": "correct_state"},
            )
        assert resp.status_code == 400

    def test_missing_state_cookie_rejected(self, client):
        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code", "state": "some_state"},
            )
        assert resp.status_code == 400

    def test_state_cookie_cleared_on_success(self, client):
        login_resp = client.get("/api/auth/login")
        state = login_resp.cookies["mindforge_oauth_state"]

        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code", "state": state},
                cookies={"mindforge_oauth_state": state},
            )
        # The response must delete the state cookie (max-age=0 or expires in the past)
        set_cookie_headers = [
            v for k, v in resp.headers.items() if k.lower() == "set-cookie"
        ]
        state_deletions = [
            h for h in set_cookie_headers
            if "mindforge_oauth_state" in h and ("max-age=0" in h or "expires=" in h.lower())
        ]
        assert state_deletions, "State cookie was not cleared after successful callback"

    def test_jwt_cookie_set_on_success(self, client):
        login_resp = client.get("/api/auth/login")
        state = login_resp.cookies["mindforge_oauth_state"]

        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code", "state": state},
                cookies={"mindforge_oauth_state": state},
            )
        assert "mindforge_token" in resp.cookies

    def test_jwt_cookie_httponly_and_samesite(self, client):
        login_resp = client.get("/api/auth/login")
        state = login_resp.cookies["mindforge_oauth_state"]

        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code", "state": state},
                cookies={"mindforge_oauth_state": state},
            )
        jwt_header = next(
            (v for k, v in resp.headers.items()
             if k.lower() == "set-cookie" and "mindforge_token" in v),
            "",
        )
        assert "httponly" in jwt_header.lower()
        assert "samesite=lax" in jwt_header.lower()

    def test_jwt_cookie_secure_by_default(self, client):
        login_resp = client.get("/api/auth/login")
        state = login_resp.cookies["mindforge_oauth_state"]

        with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
            resp = client.get(
                "/api/auth/callback",
                params={"code": "valid_code", "state": state},
                cookies={"mindforge_oauth_state": state},
            )
        jwt_header = next(
            (v for k, v in resp.headers.items()
             if k.lower() == "set-cookie" and "mindforge_token" in v),
            "",
        )
        assert "secure" in jwt_header.lower()

    def test_jwt_cookie_not_secure_when_local_dev(self, client, monkeypatch):
        import api.auth as auth_module
        login_resp = client.get("/api/auth/login")
        state = login_resp.cookies["mindforge_oauth_state"]

        with patch.object(auth_module, "_is_secure_cookie", return_value=False):
            with patch("httpx.AsyncClient", return_value=self._mock_httpx()):
                resp = client.get(
                    "/api/auth/callback",
                    params={"code": "valid_code", "state": state},
                    cookies={"mindforge_oauth_state": state},
                )
        jwt_header = next(
            (v for k, v in resp.headers.items()
             if k.lower() == "set-cookie" and "mindforge_token" in v),
            "",
        )
        assert "secure" not in jwt_header.lower()


# ---------------------------------------------------------------------------
# _is_secure_cookie() helper tests
# ---------------------------------------------------------------------------

class TestIsSecureCookie:
    def test_secure_by_default(self, monkeypatch):
        monkeypatch.delenv("LOCAL_DEV", raising=False)
        import importlib
        import api.auth as auth_module
        assert auth_module._is_secure_cookie() is True

    @pytest.mark.parametrize("val", ["true", "1", "yes"])
    def test_not_secure_when_local_dev_set(self, val, monkeypatch):
        monkeypatch.setenv("LOCAL_DEV", val)
        import api.auth as auth_module
        assert auth_module._is_secure_cookie() is False

    @pytest.mark.parametrize("val", ["false", "0", "no", ""])
    def test_secure_when_local_dev_is_falsy(self, val, monkeypatch):
        monkeypatch.setenv("LOCAL_DEV", val)
        import api.auth as auth_module
        assert auth_module._is_secure_cookie() is True
