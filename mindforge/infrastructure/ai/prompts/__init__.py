"""
Prompt loader utility.

All prompt text lives in ``*.md`` files alongside this package.
Python modules in this directory expose named constants (``SYSTEM_PROMPT``,
``USER_TEMPLATE``, etc.) by loading those files at import time via
:func:`load_prompt`.

Usage::

    from mindforge.infrastructure.ai.prompts import load_prompt

    SYSTEM_PROMPT = load_prompt("summarizer_system.md")
"""

from pathlib import Path

_PROMPTS_DIR = Path(__file__).parent


def load_prompt(filename: str) -> str:
    """Return the contents of a prompt markdown file.

    Parameters
    ----------
    filename:
        Name of the ``.md`` file relative to this package directory.

    Raises
    ------
    FileNotFoundError
        If the requested prompt file does not exist.
    """
    path = _PROMPTS_DIR / filename
    return path.read_text(encoding="utf-8")
