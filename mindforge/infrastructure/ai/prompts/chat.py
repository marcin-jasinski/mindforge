"""
Prompt templates for the Chat service.

All prompt text is stored in ``*.md`` files alongside this module and loaded
at import time.  To change a prompt, edit the corresponding markdown file.
"""

from mindforge.infrastructure.ai.prompts import load_prompt

SYSTEM_WITH_CONTEXT = load_prompt("chat_system_with_context.md")
SYSTEM_NO_CONTEXT = load_prompt("chat_system_no_context.md")
