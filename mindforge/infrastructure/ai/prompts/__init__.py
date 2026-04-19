"""
Prompt loader utility.

All prompt text lives in ``*.{locale}.md`` files alongside this package.
Python modules in this directory expose named constants (``SYSTEM_PROMPT``,
``USER_TEMPLATE``, etc.) by loading those files at import time via
:func:`load_prompt`.

File resolution order for ``load_prompt("summarizer_system.md", locale="en")``:

1. ``summarizer_system.en.md``   (requested locale)
2. ``summarizer_system.pl.md``   (fallback to default ``pl``)
3. ``summarizer_system.md``      (legacy name — backward compatibility)

Usage::

    from mindforge.infrastructure.ai.prompts import load_prompt

    SYSTEM_PROMPT = load_prompt("summarizer_system.md", "pl")
"""

from pathlib import Path

_PROMPTS_DIR = Path(__file__).parent
_DEFAULT_LOCALE = "pl"


def load_prompt(filename: str, locale: str = _DEFAULT_LOCALE) -> str:
    """Return the contents of a locale-aware prompt markdown file.

    Resolution order:
    1. ``{base}.{locale}.md`` — requested locale variant
    2. ``{base}.pl.md``       — default Polish fallback (when *locale* != 'pl')
    3. ``{filename}``         — legacy name (backward compatibility)

    Parameters
    ----------
    filename:
        Base name of the prompt file (e.g. ``"summarizer_system.md"``).
        The ``.md`` extension is stripped to compute the base name.
    locale:
        BCP-47-style locale tag (e.g. ``"pl"``, ``"en"``).  Defaults to
        ``"pl"``.

    Raises
    ------
    FileNotFoundError
        If no matching prompt file is found after exhausting all candidates.
    """
    base = filename[:-3] if filename.endswith(".md") else filename
    candidates: list[Path] = [_PROMPTS_DIR / f"{base}.{locale}.md"]
    if locale != _DEFAULT_LOCALE:
        candidates.append(_PROMPTS_DIR / f"{base}.{_DEFAULT_LOCALE}.md")
    # Legacy fallback — plain filename without locale suffix
    candidates.append(_PROMPTS_DIR / filename)
    for path in candidates:
        if path.exists():
            return path.read_text(encoding="utf-8")
    raise FileNotFoundError(
        f"No prompt file found for {filename!r} (locale={locale!r}). "
        f"Searched: {[str(p) for p in candidates]}"
    )
