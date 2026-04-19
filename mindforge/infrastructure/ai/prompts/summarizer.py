"""
Prompt template for the Summarizer agent.

All prompt text is stored in ``*.md`` files alongside this module and loaded
at import time.  To change a prompt, edit the corresponding markdown file.

Version-tagged — changing prompt files must be reflected in the agent's
``PROMPT_VERSION`` constant so that checkpoint fingerprints are invalidated.
"""

from mindforge.infrastructure.ai.prompts import load_prompt

VERSION = "1.0.0"

SYSTEM_PROMPT = load_prompt("summarizer_system.md")
USER_TEMPLATE = load_prompt("summarizer_user.md")
IMAGE_CONTEXT_TEMPLATE = load_prompt("summarizer_image_context.md")
ARTICLE_CONTEXT_TEMPLATE = load_prompt("summarizer_article_context.md")
PRIOR_CONCEPTS_TEMPLATE = load_prompt("summarizer_prior_concepts.md")
