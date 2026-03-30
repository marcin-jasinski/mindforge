"""
Tests for processor.tools.egress_policy.

Covers the acceptance criteria in the P0.3 work package:
  ✓ Requests to loopback targets are blocked
  ✓ Requests to 169.254.169.254 (cloud metadata) are blocked
  ✓ Requests to RFC1918 private ranges are blocked (unless EGRESS_ALLOW_PRIVATE=1)
  ✓ Non-HTTP(S) schemes are rejected
  ✓ Redirect responses are not automatically followed by safe_get
  ✓ Oversized responses are truncated to max_bytes
  ✓ Disallowed Content-Type responses raise EgressPolicyError
  ✓ Safe public URLs pass validation (smoke check via literal IP)
  ✓ Non-standard ports are rejected by default
"""
from __future__ import annotations

import ipaddress
import socket
from unittest.mock import MagicMock, patch

import pytest
import requests

import processor.tools.egress_policy as ep
from processor.tools.egress_policy import (
    EgressPolicyError,
    safe_get,
    validate_outbound_url,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _patch_dns(ip: str):
    """Context manager that forces getaddrinfo to return *ip* for any host."""
    family = socket.AF_INET6 if ":" in ip else socket.AF_INET
    result = [(family, socket.SOCK_STREAM, 0, "", (ip, 0))]
    return patch("processor.tools.egress_policy.socket.getaddrinfo", return_value=result)


# ---------------------------------------------------------------------------
# validate_outbound_url — scheme checks
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("bad_url", [
    "ftp://example.com/file",
    "file:///etc/passwd",
    "javascript:alert(1)",
    "data:text/html,<h1>hi</h1>",
    "ssh://user@host",
    "",
])
def test_validate_rejects_bad_schemes(bad_url: str) -> None:
    with pytest.raises(EgressPolicyError):
        validate_outbound_url(bad_url)


def test_validate_accepts_http_and_https() -> None:
    # Use literal IPs so no real DNS is needed; 93.184.216.34 = example.com
    validate_outbound_url("http://93.184.216.34/")
    validate_outbound_url("https://93.184.216.34/")


# ---------------------------------------------------------------------------
# validate_outbound_url — loopback blocking
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("loopback_url", [
    "http://127.0.0.1/",
    "http://127.0.0.1:8080/admin",
    "http://127.1.2.3/secret",
    "http://[::1]/",
])
def test_validate_blocks_loopback_literal(loopback_url: str) -> None:
    with pytest.raises(EgressPolicyError, match="blocked network"):
        validate_outbound_url(loopback_url)


def test_validate_blocks_loopback_via_dns() -> None:
    with _patch_dns("127.0.0.1"):
        with pytest.raises(EgressPolicyError, match="blocked network"):
            validate_outbound_url("http://internal.example.com/")


# ---------------------------------------------------------------------------
# validate_outbound_url — metadata service blocking
# ---------------------------------------------------------------------------

def test_validate_blocks_metadata_service_literal() -> None:
    with pytest.raises(EgressPolicyError, match="blocked network"):
        validate_outbound_url("http://169.254.169.254/latest/meta-data/")


def test_validate_blocks_metadata_service_via_dns() -> None:
    with _patch_dns("169.254.169.254"):
        with pytest.raises(EgressPolicyError, match="blocked network"):
            validate_outbound_url("http://meta.internal/")


# ---------------------------------------------------------------------------
# validate_outbound_url — private / RFC1918 blocking
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("private_url", [
    "http://10.0.0.1/",
    "http://10.255.255.255/admin",
    "http://172.16.0.1/",
    "http://172.31.255.254/",
    "http://192.168.1.1/",
    "http://192.168.0.100/secrets",
])
def test_validate_blocks_private_ranges(monkeypatch, private_url: str) -> None:
    # Ensure EGRESS_ALLOW_PRIVATE is off in this test
    monkeypatch.setattr(ep, "_ALLOW_PRIVATE", False)
    with pytest.raises(EgressPolicyError, match="private network"):
        validate_outbound_url(private_url)


def test_validate_blocks_private_via_dns(monkeypatch) -> None:
    monkeypatch.setattr(ep, "_ALLOW_PRIVATE", False)
    with _patch_dns("192.168.1.50"):
        with pytest.raises(EgressPolicyError, match="private network"):
            validate_outbound_url("http://internal.corp/")


def test_validate_allows_private_in_dev_mode(monkeypatch) -> None:
    monkeypatch.setattr(ep, "_ALLOW_PRIVATE", True)
    monkeypatch.setattr(ep, "_ALLOW_NONSTANDARD_PORTS", True)
    with _patch_dns("192.168.1.50"):
        # Should NOT raise — dev mode allows private ranges
        validate_outbound_url("http://internal.corp/")


# ---------------------------------------------------------------------------
# validate_outbound_url — port checks
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("bad_port_url", [
    "http://93.184.216.34:22/",
    "https://93.184.216.34:9200/",
    "http://93.184.216.34:3306/",
])
def test_validate_blocks_nonstandard_ports(monkeypatch, bad_port_url: str) -> None:
    monkeypatch.setattr(ep, "_ALLOW_NONSTANDARD_PORTS", False)
    with pytest.raises(EgressPolicyError, match="Port"):
        validate_outbound_url(bad_port_url)


@pytest.mark.parametrize("ok_port_url", [
    "http://93.184.216.34:80/",
    "https://93.184.216.34:443/",
    "http://93.184.216.34:8080/",
    "https://93.184.216.34:8443/",
    "http://93.184.216.34/",   # default port (None)
])
def test_validate_accepts_standard_ports(monkeypatch, ok_port_url: str) -> None:
    monkeypatch.setattr(ep, "_ALLOW_NONSTANDARD_PORTS", False)
    # literal IPs, no DNS needed
    validate_outbound_url(ok_port_url)


# ---------------------------------------------------------------------------
# safe_get — redirects are NOT followed
# ---------------------------------------------------------------------------

def test_safe_get_does_not_follow_redirects() -> None:
    mock_resp = MagicMock(spec=requests.Response)
    mock_resp.status_code = 301
    mock_resp.headers = {"Content-Type": "text/html", "Location": "http://evil.internal/"}
    mock_resp.raise_for_status.return_value = None
    mock_resp.iter_content.return_value = iter([b"redirecting"])

    with patch("processor.tools.egress_policy.requests.get", return_value=mock_resp) as mock_get:
        resp = safe_get("http://93.184.216.34/", timeout=5)
        _kwargs = mock_get.call_args.kwargs
        assert _kwargs.get("allow_redirects") is False, "allow_redirects must be False"


# ---------------------------------------------------------------------------
# safe_get — response size capping
# ---------------------------------------------------------------------------

def test_safe_get_truncates_oversized_response() -> None:
    big_chunk = b"x" * 100_000
    # Build a response that would exceed the 1-byte max_bytes limit
    mock_resp = MagicMock(spec=requests.Response)
    mock_resp.status_code = 200
    mock_resp.headers = {"Content-Type": "text/html"}
    mock_resp.raise_for_status.return_value = None
    mock_resp.iter_content.return_value = iter([big_chunk, big_chunk])
    mock_resp.apparent_encoding = "utf-8"

    with patch("processor.tools.egress_policy.requests.get", return_value=mock_resp):
        resp = safe_get("http://93.184.216.34/", max_bytes=500)

    assert len(resp._content) <= 500


# ---------------------------------------------------------------------------
# safe_get — content-type check
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("bad_ct", [
    "application/octet-stream",
    "image/png",
    "application/pdf",
    "video/mp4",
])
def test_safe_get_rejects_disallowed_content_types(bad_ct: str) -> None:
    mock_resp = MagicMock(spec=requests.Response)
    mock_resp.status_code = 200
    mock_resp.headers = {"Content-Type": bad_ct}
    mock_resp.raise_for_status.return_value = None
    mock_resp.iter_content.return_value = iter([b"data"])
    mock_resp.close.return_value = None

    with patch("processor.tools.egress_policy.requests.get", return_value=mock_resp):
        with pytest.raises(EgressPolicyError, match="Content-Type"):
            safe_get("http://93.184.216.34/")
