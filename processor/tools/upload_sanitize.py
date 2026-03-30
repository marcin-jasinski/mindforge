"""
Shared upload filename sanitisation utilities.

Used by both the REST API upload path (api/routers/lessons.py) and the
Discord bot upload cog (discord_bot/cogs/upload.py) to ensure that
attacker-controlled filenames can never escape the intended upload directory.

Design decisions
----------------
* Any filename that contains a path separator ('/', '\\') or a Windows drive
  prefix ('X:') is **rejected outright**.  Silently stripping directory
  components would mask the anomaly and could confuse callers about what was
  actually written.  Modern upload clients (browsers, Discord) send only the
  bare filename; a path separator in the upload field is a red flag.
* After the separator/drive check the name is additionally validated as a
  PurePosixPath to catch any remaining edge cases before the final
  containment check.
* After concatenation with the upload root the resolved path is verified to
  remain inside that root (defence in depth).
* Collision behaviour: append _1, _2, ... rather than silently overwriting.
"""
from __future__ import annotations

import re
from pathlib import Path, PurePosixPath


# Characters that are always illegal in filenames regardless of OS.
_ILLEGAL_CHARS = frozenset('\x00\r\n')

# Pattern for Windows drive letter prefix: one letter followed by colon.
_DRIVE_PREFIX_RE = re.compile(r'^[A-Za-z]:')


def sanitize_upload_filename(raw_name: str) -> str:
    """Validate and return a safe upload filename.

    Parameters
    ----------
    raw_name:
        The filename string as received from an upload request.

    Returns
    -------
    str
        The validated filename (identical to *raw_name* if it is safe).

    Raises
    ------
    ValueError
        When the filename is empty; contains illegal characters (null bytes,
        newlines); contains path separators ('/' or '\\\\'); contains a Windows
        drive prefix; resolves to an empty basename; or is a dot-only or
        dot-prefixed name.
    """
    if not raw_name:
        raise ValueError("Filename must not be empty")

    if any(ch in raw_name for ch in _ILLEGAL_CHARS):
        raise ValueError("Filename contains illegal characters (null byte or newline)")

    # Reject any path separators — both POSIX ('/') and Windows ('\\').
    if '/' in raw_name or '\\' in raw_name:
        raise ValueError(
            f"Filename {raw_name!r} contains path separators and is not allowed; "
            "supply a bare filename without directory components"
        )

    # Reject Windows drive prefixes ('C:', 'D:', etc.)
    if _DRIVE_PREFIX_RE.match(raw_name):
        raise ValueError(
            f"Filename {raw_name!r} contains a Windows drive prefix and is not allowed"
        )

    # Normalise via PurePosixPath to catch any remaining edge cases and
    # extract the final name component.
    name: str = PurePosixPath(raw_name).name

    if not name:
        raise ValueError(
            f"Filename {raw_name!r} resolves to an empty string"
        )

    # Reject dot-only names: '.', '..', '...', etc.
    if not name.replace(".", ""):
        raise ValueError(f"Filename {name!r} is a dot-only name and is not allowed")

    # Reject hidden / dot-prefixed names (e.g. '.bashrc', '.env').
    if name.startswith("."):
        raise ValueError(
            f"Filenames starting with '.' are not allowed: {name!r}"
        )

    return name


def safe_upload_dest(upload_root: Path, sanitized_name: str) -> Path:
    """Compute the destination path and verify it stays within *upload_root*.

    Parameters
    ----------
    upload_root:
        The intended parent directory.  Will be resolved to an absolute path.
    sanitized_name:
        A filename already validated by :func:`sanitize_upload_filename`.

    Returns
    -------
    Path
        Resolved absolute path of the upload destination.

    Raises
    ------
    ValueError
        When the resolved destination escapes *upload_root*.
    """
    upload_root = upload_root.resolve()
    dest = (upload_root / sanitized_name).resolve()

    try:
        dest.relative_to(upload_root)
    except ValueError:
        raise ValueError(
            f"Resolved upload path {str(dest)!r} escapes intended upload "
            f"directory {str(upload_root)!r}"
        )

    return dest


def unique_upload_dest(upload_root: Path, raw_name: str) -> Path:
    """Return a safe, unique destination path within *upload_root*.

    Combines :func:`sanitize_upload_filename` and :func:`safe_upload_dest`
    and adds collision avoidance: if *sanitized_name* already exists, the
    function appends ``_1``, ``_2``, … to the stem until it finds a free slot.

    Parameters
    ----------
    upload_root:
        The intended parent directory.
    raw_name:
        The raw filename from the upload request (will be sanitised).

    Returns
    -------
    Path
        Resolved, unique destination path inside *upload_root*.

    Raises
    ------
    ValueError
        When the filename fails sanitisation or escapes the upload root.
    RuntimeError
        When no free filename slot is found within 999 candidates.
    """
    sanitized = sanitize_upload_filename(raw_name)
    dest = safe_upload_dest(upload_root, sanitized)
    if not dest.exists():
        return dest

    stem = Path(sanitized).stem
    suffix = Path(sanitized).suffix
    for i in range(1, 1000):
        candidate_name = f"{stem}_{i}{suffix}"
        candidate = safe_upload_dest(upload_root, candidate_name)
        if not candidate.exists():
            return candidate

    raise RuntimeError(
        f"Cannot find a free filename for {sanitized!r} in {str(upload_root)!r}"
    )
