"""
Prompt template for the RelevanceGuard agent.

All prompt text is stored in ``*.md`` files alongside this module and loaded
at import time.  To change a prompt, edit the corresponding markdown file.

Version-tagged — changing prompt files must be reflected in the agent's
``PROMPT_VERSION`` constant so that checkpoint fingerprints are invalidated.
"""

from mindforge.infrastructure.ai.prompts import load_prompt

VERSION = "1.0.0"

SYSTEM_PROMPT = load_prompt("relevance_guard_system.md")
