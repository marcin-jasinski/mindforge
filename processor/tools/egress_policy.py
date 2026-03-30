"""
Outbound URL safety policy for MindForge.

All server-side HTTP(S) fetches of user-supplied content (lesson article
links, lesson image URLs) MUST pass through :func:`validate_outbound_url`
before the request is made.

SSRF-protection strategy
-------------------------
1. Scheme is restricted to http / https.
2. Port is restricted to 80 / 443 / 8080 / 8443 and the default implied
   port (None), unless ``EGRESS_ALLOW_NONSTANDARD_PORTS=1`` is set (dev-only).
3. The *hostname* is resolved to IP addresses before the request is
   constructed so we can check the resolved target, not just the textual
   host.
4. Every resolved IP is tested against blocked ranges:
     - loopback          127.0.0.0/8, ::1
     - unspecified       0.0.0.0, ::
     - link-local        169.254.0.0/16 (AWS/GCP metadata), fe80::/10
     - private / RFC1918 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
     - unique-local IPv6 fc00::/7
     - multicast         224.0.0.0/4, ff00::/8
     - cloud-metadata    specifically 169.254.169.254
5. Redirect-following is disabled: callers receive a plain ``requests.get``
   with ``allow_redirects=False`` from :func:`safe_get`.  Callers that need
   to follow redirects MUST call :func:`safe_get` for each hop explicitly
   (not yet needed in MindForge).

Public-mode vs local-dev
-------------------------
* By default all private/loopback ranges are blocked.
* Set ``EGRESS_ALLOW_PRIVATE=1`` to allow private ranges (loopback still
  blocked).  This is intended **only** for local development where the
  MindForge instance itself runs behind a private network.
* ``EGRESS_ALLOW_PRIVATE`` is never honoured when
  ``MINDFORGE_PUBLIC_MODE=1`` is set; the latter takes precedence.

Usage
-----
::

    from processor.tools.egress_policy import validate_outbound_url, safe_get

    # raises EgressPolicyError on violation; returns normalised URL on success
    safe_url = validate_outbound_url(raw_url)
    response  = safe_get(safe_url, timeout=10)
"""
from __future__ import annotations

import ipaddress
import logging
import os
import socket
from typing import Any
from urllib.parse import urlparse

import requests

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration (read once at import time)
# ---------------------------------------------------------------------------

_PUBLIC_MODE: bool = os.environ.get("MINDFORGE_PUBLIC_MODE", "").strip() not in ("", "0", "false", "no")
_ALLOW_PRIVATE: bool = (
    not _PUBLIC_MODE
    and os.environ.get("EGRESS_ALLOW_PRIVATE", "").strip() not in ("", "0", "false", "no")
)
_ALLOW_NONSTANDARD_PORTS: bool = (
    not _PUBLIC_MODE
    and os.environ.get("EGRESS_ALLOW_NONSTANDARD_PORTS", "").strip() not in ("", "0", "false", "no")
)

# Allowed schemes
_ALLOWED_SCHEMES = frozenset({"http", "https"})

# Allowed ports; None means the scheme-default port (80 / 443)
_ALLOWED_PORTS = frozenset({None, 80, 443, 8080, 8443})

# Blocked IP networks (checked after DNS resolution)
_BLOCKED_NETWORKS = [
    # Loopback — always blocked even in dev mode
    ipaddress.ip_network("127.0.0.0/8"),
    ipaddress.ip_network("::1/128"),
    # Unspecified
    ipaddress.ip_network("0.0.0.0/32"),
    ipaddress.ip_network("::/128"),
    # Link-local / metadata service
    ipaddress.ip_network("169.254.0.0/16"),
    ipaddress.ip_network("fe80::/10"),
]

_PRIVATE_NETWORKS = [
    # RFC 1918
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
    # Unique-local IPv6
    ipaddress.ip_network("fc00::/7"),
    # Multicast
    ipaddress.ip_network("224.0.0.0/4"),
    ipaddress.ip_network("ff00::/8"),
    # Shared address space (RFC 6598)
    ipaddress.ip_network("100.64.0.0/10"),
    # Documentation ranges (shouldn't be routed)
    ipaddress.ip_network("192.0.2.0/24"),
    ipaddress.ip_network("198.51.100.0/24"),
    ipaddress.ip_network("203.0.113.0/24"),
]


# ---------------------------------------------------------------------------
# Public exception
# ---------------------------------------------------------------------------

class EgressPolicyError(ValueError):
    """Raised when a URL violates the outbound egress policy."""


# ---------------------------------------------------------------------------
# Core validation
# ---------------------------------------------------------------------------

def validate_outbound_url(raw_url: str) -> str:
    """Validate *raw_url* against the egress policy.

    Parameters
    ----------
    raw_url:
        The URL as extracted from untrusted lesson content.

    Returns
    -------
    str
        The (unchanged) URL if it passes all checks.

    Raises
    ------
    EgressPolicyError
        On any policy violation: bad scheme, bad port, private/loopback host,
        or DNS resolution failure.
    """
    if not raw_url or not isinstance(raw_url, str):
        raise EgressPolicyError("URL must be a non-empty string")

    try:
        parsed = urlparse(raw_url)
    except Exception as exc:
        raise EgressPolicyError(f"Unparseable URL {raw_url!r}: {exc}") from exc

    # 1. Scheme check
    scheme = (parsed.scheme or "").lower()
    if scheme not in _ALLOWED_SCHEMES:
        from processor import metrics
        metrics.increment("egress_blocked")
        raise EgressPolicyError(
            f"Scheme {scheme!r} is not allowed (only http/https); URL: {raw_url!r}"
        )

    # 2. Hostname required
    hostname = parsed.hostname
    if not hostname:
        from processor import metrics
        metrics.increment("egress_blocked")
        raise EgressPolicyError(f"No hostname in URL {raw_url!r}")

    # 3. Port check
    port = parsed.port  # None means default for scheme
    if not _ALLOW_NONSTANDARD_PORTS and port not in _ALLOWED_PORTS:
        from processor import metrics
        metrics.increment("egress_blocked")
        raise EgressPolicyError(
            f"Port {port} is not in the allowed set {_ALLOWED_PORTS}; URL: {raw_url!r}"
        )

    # 4. DNS resolution + IP range check
    _check_host_safety(hostname, raw_url)

    return raw_url


def _check_host_safety(hostname: str, raw_url: str) -> None:
    """Resolve *hostname* and verify every resolved IP is safe."""
    # First check if the hostname is already a literal IP
    try:
        addr = ipaddress.ip_address(hostname)
        _check_ip_safe(addr, raw_url)
        return
    except ValueError:
        pass  # not a literal IP, proceed to DNS resolution

    try:
        resolved = socket.getaddrinfo(hostname, None)
    except socket.gaierror as exc:
        raise EgressPolicyError(
            f"DNS resolution failed for {hostname!r} in URL {raw_url!r}: {exc}"
        ) from exc

    if not resolved:
        raise EgressPolicyError(
            f"DNS resolution returned no addresses for {hostname!r} in URL {raw_url!r}"
        )

    for _family, _type, _proto, _canonname, sockaddr in resolved:
        ip_str = sockaddr[0]
        try:
            addr = ipaddress.ip_address(ip_str)
        except ValueError:
            continue
        _check_ip_safe(addr, raw_url)


def _check_ip_safe(addr: ipaddress.IPv4Address | ipaddress.IPv6Address, raw_url: str) -> None:
    """Raise :exc:`EgressPolicyError` if *addr* is in a blocked range."""
    from processor import metrics  # local import to avoid circular dependencies

    # Always-blocked ranges (loopback, link-local, unspecified)
    for net in _BLOCKED_NETWORKS:
        if addr in net:
            metrics.increment("egress_blocked")
            raise EgressPolicyError(
                f"Resolved IP {addr} is in blocked network {net}; URL: {raw_url!r}"
            )

    # Private ranges — blocked unless _ALLOW_PRIVATE is set in dev mode
    if not _ALLOW_PRIVATE:
        for net in _PRIVATE_NETWORKS:
            if addr in net:
                metrics.increment("egress_blocked")
                raise EgressPolicyError(
                    f"Resolved IP {addr} is in private network {net}; URL: {raw_url!r}. "
                    "Set EGRESS_ALLOW_PRIVATE=1 for local development."
                )


# ---------------------------------------------------------------------------
# Safe HTTP helper
# ---------------------------------------------------------------------------

MAX_RESPONSE_BYTES = 5 * 1024 * 1024  # 5 MB hard cap
_DEFAULT_TIMEOUT = (5, 15)  # (connect, read) seconds
_DEFAULT_HEADERS = {"User-Agent": "MindForge/1.0 (security-hardened fetcher)"}

ALLOWED_CONTENT_TYPES = frozenset({
    "text/html",
    "text/plain",
    "application/xhtml+xml",
    "application/xml",
    "text/xml",
})


def safe_get(
    url: str,
    *,
    timeout: int | tuple[int, int] = _DEFAULT_TIMEOUT,
    extra_headers: dict[str, str] | None = None,
    max_bytes: int = MAX_RESPONSE_BYTES,
) -> requests.Response:
    """Perform a GET request to a validated URL with safety guardrails.

    - Redirects are **disabled**.  If the server responds with a redirect
      the caller receives the 3xx response and can decide what to do.
    - Response size is hard-capped at *max_bytes* (body is streamed and
      truncated rather than buffered into memory).
    - Content-Type is checked: only text-based types are returned as-is;
      others raise :exc:`EgressPolicyError`.

    Parameters
    ----------
    url:
        Must already have been passed through :func:`validate_outbound_url`.
    timeout:
        ``(connect_timeout, read_timeout)`` in seconds, or a single int.
    extra_headers:
        Additional HTTP headers to merge into the default set.
    max_bytes:
        Maximum bytes of response body to read.

    Returns
    -------
    requests.Response

    Raises
    ------
    EgressPolicyError
        On content-type violations or oversized responses.
    requests.RequestException
        On network errors.
    """
    headers = {**_DEFAULT_HEADERS, **(extra_headers or {})}

    resp = requests.get(
        url,
        timeout=timeout,
        headers=headers,
        allow_redirects=False,
        stream=True,
    )
    resp.raise_for_status()

    # Content-type gate
    ct = resp.headers.get("Content-Type", "").split(";")[0].strip().lower()
    if ct and ct not in ALLOWED_CONTENT_TYPES:
        resp.close()
        raise EgressPolicyError(
            f"Response Content-Type {ct!r} is not in the allowed set for URL {url!r}"
        )

    # Stream and cap response body
    chunks: list[bytes] = []
    total = 0
    truncated = False
    for chunk in resp.iter_content(chunk_size=65536):
        total += len(chunk)
        if total > max_bytes:
            chunks.append(chunk[: max_bytes - (total - len(chunk))])
            truncated = True
            break
        chunks.append(chunk)

    resp.close()

    # Re-assemble the body into the response object so callers can use resp.text
    resp._content = b"".join(chunks)  # type: ignore[attr-defined]
    resp.encoding = resp.apparent_encoding or "utf-8"

    if truncated:
        log.warning(
            "Response from %s truncated to %d bytes (limit: %d)",
            url[:80],
            max_bytes,
            max_bytes,
        )

    return resp
