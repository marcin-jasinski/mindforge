"""
Discord OAuth2 authentication for MindForge API.

Flow:
  1. GET /api/auth/login       → redirect to Discord OAuth authorize URL
                                  (sets a short-lived state cookie for CSRF protection)
  2. GET /api/auth/callback    → validate state, exchange code for token, fetch user,
                                  set JWT cookie
  3. GET /api/auth/me          → return current user info from JWT
  4. POST /api/auth/logout     → clear JWT cookie

JWT is stored in an httponly cookie. Single-user gate via ALLOWED_DISCORD_USER_ID.

Cookie security:
  By default the auth cookie is ``HttpOnly``, ``SameSite=Lax``, and marked ``Secure``
  so browsers only send it over HTTPS.  Set ``LOCAL_DEV=true`` in ``.env`` to suppress
  the ``Secure`` flag for plain HTTP development servers.
"""
from __future__ import annotations

import logging
import os
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any
from urllib.parse import quote

import httpx
from fastapi import APIRouter, HTTPException, Request, Response
from jose import JWTError, jwt

from api.schemas import UserInfo

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/auth", tags=["auth"])

DISCORD_API_BASE = "https://discord.com/api/v10"
COOKIE_NAME = "mindforge_token"
_STATE_COOKIE = "mindforge_oauth_state"
JWT_ALGORITHM = "HS256"
JWT_EXPIRY_DAYS = 30
_STATE_MAX_AGE = 300  # 5 minutes — more than enough for an OAuth round-trip


def _get_env(key: str, required: bool = True) -> str:
    val = os.environ.get(key, "").strip()
    if required and not val:
        raise RuntimeError(f"Missing required env var: {key}")
    return val


def _is_secure_cookie() -> bool:
    """Return True unless LOCAL_DEV=true is explicitly set."""
    raw = os.environ.get("LOCAL_DEV", "").strip().lower()
    return raw not in ("1", "true", "yes")


# ── Routes ──────────────────────────────────────────────────────────


@router.get("/login")
async def login(response: Response):
    """Redirect user to Discord OAuth2 authorize page, setting a CSRF state cookie."""
    client_id = _get_env("DISCORD_CLIENT_ID")
    redirect_uri = _get_env("DISCORD_REDIRECT_URI")

    state = secrets.token_urlsafe(32)

    from fastapi.responses import RedirectResponse

    params = (
        f"client_id={client_id}"
        f"&redirect_uri={quote(redirect_uri, safe='')}"
        f"&response_type=code"
        f"&scope=identify"
        f"&state={state}"
    )
    resp = RedirectResponse(f"https://discord.com/oauth2/authorize?{params}")
    resp.set_cookie(
        key=_STATE_COOKIE,
        value=state,
        httponly=True,
        samesite="lax",
        max_age=_STATE_MAX_AGE,
        path="/api/auth/callback",
        secure=_is_secure_cookie(),
    )
    return resp


@router.get("/callback")
async def callback(request: Request, code: str, state: str | None = None):
    """Validate OAuth state, exchange Discord auth code for user info, set JWT cookie."""
    # ── CSRF state validation ──────────────────────────────────────
    stored_state = request.cookies.get(_STATE_COOKIE)
    if not stored_state or not state or not secrets.compare_digest(stored_state, state):
        raise HTTPException(status_code=400, detail="Invalid or missing OAuth state")

    client_id = _get_env("DISCORD_CLIENT_ID")
    client_secret = _get_env("DISCORD_CLIENT_SECRET")
    redirect_uri = _get_env("DISCORD_REDIRECT_URI")
    jwt_secret = _get_env("JWT_SECRET")

    # Exchange code for access token
    async with httpx.AsyncClient() as client:
        token_resp = await client.post(
            f"{DISCORD_API_BASE}/oauth2/token",
            data={
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": redirect_uri,
            },
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            auth=(client_id, client_secret),
        )
        if token_resp.status_code != 200:
            log.error("Discord token exchange failed: %s", token_resp.text)
            raise HTTPException(status_code=401, detail="Discord auth failed")

        access_token = token_resp.json().get("access_token")
        if not access_token:
            raise HTTPException(status_code=401, detail="No access token received")

        # Fetch user info
        user_resp = await client.get(
            f"{DISCORD_API_BASE}/users/@me",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        if user_resp.status_code != 200:
            raise HTTPException(status_code=401, detail="Failed to fetch Discord user")

        user_data = user_resp.json()

    discord_id = user_data["id"]
    username = user_data.get("username", "")
    avatar = user_data.get("avatar")

    # Single-user gate
    allowed_id = os.environ.get("ALLOWED_DISCORD_USER_ID", "").strip()
    if allowed_id and discord_id != allowed_id:
        raise HTTPException(status_code=403, detail="Access denied")

    # Create JWT
    payload = {
        "sub": discord_id,
        "username": username,
        "avatar": avatar,
        "exp": datetime.now(timezone.utc) + timedelta(days=JWT_EXPIRY_DAYS),
    }
    token = jwt.encode(payload, jwt_secret, algorithm=JWT_ALGORITHM)

    # Set httponly cookie and redirect to app root
    from fastapi.responses import RedirectResponse
    resp = RedirectResponse("/", status_code=302)
    resp.set_cookie(
        key=COOKIE_NAME,
        value=token,
        httponly=True,
        samesite="lax",
        max_age=JWT_EXPIRY_DAYS * 86400,
        path="/",
        secure=_is_secure_cookie(),
    )
    # Clear the consumed state cookie
    resp.delete_cookie(_STATE_COOKIE, path="/api/auth/callback")
    return resp


@router.get("/me", response_model=UserInfo)
async def me(request: Request):
    """Return current authenticated user info."""
    user = get_current_user(request)
    return user


@router.post("/logout")
async def logout(response: Response):
    """Clear auth cookie."""
    response.delete_cookie(COOKIE_NAME, path="/")
    return {"message": "logged out"}


# ── Middleware helper ───────────────────────────────────────────────


def get_current_user(request: Request) -> UserInfo:
    """Extract and validate user from JWT cookie. Raises 401 if invalid."""
    token = request.cookies.get(COOKIE_NAME)
    if not token:
        raise HTTPException(status_code=401, detail="Not authenticated")

    jwt_secret = _get_env("JWT_SECRET")
    try:
        payload = jwt.decode(token, jwt_secret, algorithms=[JWT_ALGORITHM])
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    return UserInfo(
        discord_id=payload["sub"],
        username=payload.get("username", ""),
        avatar=payload.get("avatar"),
    )


def require_auth(request: Request) -> UserInfo:
    """FastAPI dependency that enforces authentication."""
    return get_current_user(request)
