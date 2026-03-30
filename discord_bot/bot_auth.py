"""
Discord bot access-control policy for MindForge.

Reads allowlists from environment variables and exposes a single
:func:`check_access` guard that every slash-command handler calls
before executing any business logic.

Configuration (env vars, all optional)
----------------------------------------
DISCORD_ALLOWED_GUILD_IDS
    Comma-separated list of guild (server) snowflake IDs that may use the
    bot.  If empty the bot is unrestricted by guild *only if*
    DISCORD_ALLOWED_USER_IDS is also non-empty (user-scoped allow).
    Example: ``DISCORD_ALLOWED_GUILD_IDS=123456789012345678,987654321098765432``

DISCORD_ALLOWED_ROLE_IDS
    Comma-separated list of role snowflake IDs (across any guild) that are
    permitted to execute commands.  Checked in addition to guild membership.
    Example: ``DISCORD_ALLOWED_ROLE_IDS=111111111111111111``

DISCORD_ALLOWED_USER_IDS
    Comma-separated list of Discord user snowflake IDs that are explicitly
    permitted regardless of guild or role.
    Example: ``DISCORD_ALLOWED_USER_IDS=222222222222222222``

DISCORD_REQUIRE_AUTH
    Set to ``1`` / ``true`` / ``yes`` to make the bot refuse to start unless
    at least one of the three allowlists is non-empty.  Defaults to ``true``
    for safety — set to ``false`` only for well-understood public bots.

Access decision logic
----------------------
A user is allowed when ANY of the following is true:

1. Their user ID is in DISCORD_ALLOWED_USER_IDS.
2. The command is invoked in a guild whose ID is in DISCORD_ALLOWED_GUILD_IDS
   AND (DISCORD_ALLOWED_ROLE_IDS is empty OR the user has at least one of
   those roles in that guild).
3. All three allowlists are empty AND DISCORD_REQUIRE_AUTH=false — the bot
   is in unrestricted public mode (not recommended).

Usage in cog command handlers
-------------------------------
::

    from discord_bot.bot_auth import check_access

    @app_commands.command(name="quiz", ...)
    async def quiz(self, interaction: discord.Interaction, ...) -> None:
        if not await check_access(interaction):
            return          # denial message already sent
        ...
"""
from __future__ import annotations

import logging
import os

import discord

log = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Parse allowlists once at import time
# ---------------------------------------------------------------------------

def _parse_ids(env_key: str) -> frozenset[int]:
    raw = os.environ.get(env_key, "").strip()
    if not raw:
        return frozenset()
    ids: set[int] = set()
    for part in raw.split(","):
        part = part.strip()
        if part:
            try:
                ids.add(int(part))
            except ValueError:
                log.warning("Invalid snowflake ID in %s: %r — ignored", env_key, part)
    return frozenset(ids)


ALLOWED_GUILD_IDS: frozenset[int] = _parse_ids("DISCORD_ALLOWED_GUILD_IDS")
ALLOWED_ROLE_IDS: frozenset[int] = _parse_ids("DISCORD_ALLOWED_ROLE_IDS")
ALLOWED_USER_IDS: frozenset[int] = _parse_ids("DISCORD_ALLOWED_USER_IDS")

_REQUIRE_AUTH_RAW = os.environ.get("DISCORD_REQUIRE_AUTH", "true").strip().lower()
REQUIRE_AUTH: bool = _REQUIRE_AUTH_RAW not in ("0", "false", "no")

_ANY_ALLOWLIST = bool(ALLOWED_GUILD_IDS or ALLOWED_ROLE_IDS or ALLOWED_USER_IDS)


# ---------------------------------------------------------------------------
# Startup check
# ---------------------------------------------------------------------------

def validate_auth_config() -> None:
    """Call at bot startup; raises RuntimeError if config is too permissive.

    Raises
    ------
    RuntimeError
        When DISCORD_REQUIRE_AUTH=true (default) and no allowlist is
        configured — this would allow any Discord user to spend LLM budget.
    """
    if REQUIRE_AUTH and not _ANY_ALLOWLIST:
        raise RuntimeError(
            "MindForge Discord bot requires at least one access-control allowlist "
            "to be configured:\n"
            "  DISCORD_ALLOWED_GUILD_IDS — restrict to specific servers\n"
            "  DISCORD_ALLOWED_ROLE_IDS  — restrict to specific roles\n"
            "  DISCORD_ALLOWED_USER_IDS  — restrict to specific users\n"
            "Set DISCORD_REQUIRE_AUTH=false to run without restrictions (not recommended)."
        )
    if not REQUIRE_AUTH and not _ANY_ALLOWLIST:
        log.warning(
            "MindForge bot is running WITHOUT any access restrictions "
            "(DISCORD_REQUIRE_AUTH=false and no allowlists configured). "
            "Anyone can trigger LLM calls."
        )


# ---------------------------------------------------------------------------
# Per-interaction access check
# ---------------------------------------------------------------------------

_DENIAL_MSG = (
    "❌ Nie masz uprawnień do korzystania z tego bota. "
    "Skontaktuj się z administratorem, jeśli uważasz, że to błąd."
)


async def check_access(interaction: discord.Interaction) -> bool:
    """Return True if the interaction is authorised; send an ephemeral denial and return False otherwise.

    Parameters
    ----------
    interaction:
        The Discord interaction to check.
    """
    user = interaction.user
    if not isinstance(user, (discord.Member, discord.User)):
        await _deny(interaction)
        return False

    user_id: int = user.id

    # Fast path: explicitly allowed user
    if ALLOWED_USER_IDS and user_id in ALLOWED_USER_IDS:
        return True

    # Guild + role check
    if ALLOWED_GUILD_IDS:
        guild = interaction.guild
        if guild is None or guild.id not in ALLOWED_GUILD_IDS:
            await _deny(interaction)
            return False

        # Guild is allowed — now check role restriction (if any)
        if ALLOWED_ROLE_IDS and isinstance(user, discord.Member):
            member_role_ids = frozenset(r.id for r in user.roles)
            if not (member_role_ids & ALLOWED_ROLE_IDS):
                await _deny(interaction)
                return False

        return True

    # No guild allowlist and user not explicitly allowed
    if not _ANY_ALLOWLIST and not REQUIRE_AUTH:
        # Unrestricted public mode — intentionally configured to be open
        return True

    # Fallthrough: allowlists exist but user matched none of them
    await _deny(interaction)
    return False


async def _deny(interaction: discord.Interaction) -> None:
    """Send an ephemeral denial message, handling both fresh and deferred interactions."""
    try:
        if interaction.response.is_done():
            await interaction.followup.send(_DENIAL_MSG, ephemeral=True)
        else:
            await interaction.response.send_message(_DENIAL_MSG, ephemeral=True)
    except Exception:
        log.warning("Failed to send denial message to user %s", interaction.user, exc_info=True)
