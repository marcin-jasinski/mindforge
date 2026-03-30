"""
Tests for discord_bot.bot_auth — P0.4 Discord authorization boundary.

Covers:
  ✓ validate_auth_config() raises RuntimeError when REQUIRE_AUTH=True and no allowlists
  ✓ validate_auth_config() passes when guild allowlist is set
  ✓ validate_auth_config() passes when user allowlist is set
  ✓ validate_auth_config() passes (with warning) when REQUIRE_AUTH=False and no lists
  ✓ check_access() allows user explicitly in ALLOWED_USER_IDS
  ✓ check_access() denies user not in any allowlist when REQUIRE_AUTH=True
  ✓ check_access() allows command in allowed guild (no role restriction)
  ✓ check_access() denies command in disallowed guild
  ✓ check_access() denies DM (no guild) when guild allowlist set
  ✓ check_access() allows member with matching role in allowed guild
  ✓ check_access() denies member without matching role in allowed guild
  ✓ check_access() user-ID fast path bypasses guild+role check
  ✓ check_access() unrestricted public mode (REQUIRE_AUTH=False, no lists)
  ✓ check_access() sends ephemeral denial on rejection
"""
from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

import discord_bot.bot_auth as bot_auth


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_interaction(
    user_id: int = 999,
    guild_id: int | None = 111,
    role_ids: list[int] | None = None,
    is_member: bool = True,
    response_done: bool = False,
) -> MagicMock:
    """Build a minimal mock discord.Interaction."""
    import discord

    interaction = MagicMock()

    if is_member:
        user = MagicMock(spec=discord.Member)
    else:
        user = MagicMock(spec=discord.User)
    user.id = user_id

    if is_member and role_ids is not None:
        roles = []
        for rid in role_ids:
            role = MagicMock()
            role.id = rid
            roles.append(role)
        user.roles = roles
    elif is_member:
        user.roles = []

    interaction.user = user

    if guild_id is not None:
        guild = MagicMock()
        guild.id = guild_id
        interaction.guild = guild
    else:
        interaction.guild = None

    # Mock response
    response = MagicMock()
    response.is_done.return_value = response_done
    response.send_message = AsyncMock()
    interaction.response = response
    interaction.followup = MagicMock()
    interaction.followup.send = AsyncMock()

    return interaction


# ---------------------------------------------------------------------------
# validate_auth_config() tests
# ---------------------------------------------------------------------------

class TestValidateAuthConfig:
    def test_raises_when_require_auth_and_no_allowlists(self):
        with patch.multiple(
            bot_auth,
            REQUIRE_AUTH=True,
            _ANY_ALLOWLIST=False,
        ):
            with pytest.raises(RuntimeError, match="requires at least one access-control allowlist"):
                bot_auth.validate_auth_config()

    def test_passes_when_guild_allowlist_set(self):
        with patch.multiple(
            bot_auth,
            REQUIRE_AUTH=True,
            _ANY_ALLOWLIST=True,
        ):
            bot_auth.validate_auth_config()  # must not raise

    def test_passes_when_user_allowlist_set(self):
        with patch.multiple(
            bot_auth,
            REQUIRE_AUTH=True,
            _ANY_ALLOWLIST=True,
        ):
            bot_auth.validate_auth_config()  # must not raise

    def test_passes_with_warning_when_require_auth_false_and_no_lists(self):
        with patch.multiple(
            bot_auth,
            REQUIRE_AUTH=False,
            _ANY_ALLOWLIST=False,
        ):
            bot_auth.validate_auth_config()  # must not raise (logs a warning)


# ---------------------------------------------------------------------------
# check_access() tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
class TestCheckAccess:
    async def test_allows_user_in_allowed_user_ids(self):
        interaction = _make_interaction(user_id=123)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset([123]),
            ALLOWED_GUILD_IDS=frozenset(),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is True

    async def test_denies_user_not_in_any_allowlist(self):
        interaction = _make_interaction(user_id=999, guild_id=None)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset([1]),
            ALLOWED_GUILD_IDS=frozenset(),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is False

    async def test_allows_command_in_allowed_guild_no_role_restriction(self):
        interaction = _make_interaction(user_id=999, guild_id=111)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset(),
            ALLOWED_GUILD_IDS=frozenset([111]),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is True

    async def test_denies_command_in_disallowed_guild(self):
        interaction = _make_interaction(user_id=999, guild_id=999)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset(),
            ALLOWED_GUILD_IDS=frozenset([111]),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is False

    async def test_denies_dm_when_guild_allowlist_set(self):
        interaction = _make_interaction(user_id=999, guild_id=None)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset(),
            ALLOWED_GUILD_IDS=frozenset([111]),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is False

    async def test_allows_member_with_matching_role_in_allowed_guild(self):
        interaction = _make_interaction(user_id=999, guild_id=111, role_ids=[500, 501])
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset(),
            ALLOWED_GUILD_IDS=frozenset([111]),
            ALLOWED_ROLE_IDS=frozenset([501]),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is True

    async def test_denies_member_without_matching_role_in_allowed_guild(self):
        interaction = _make_interaction(user_id=999, guild_id=111, role_ids=[999])
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset(),
            ALLOWED_GUILD_IDS=frozenset([111]),
            ALLOWED_ROLE_IDS=frozenset([501]),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is False

    async def test_user_id_fast_path_bypasses_guild_and_role_check(self):
        # User is in ALLOWED_USER_IDS but NOT in the guild allowlist
        interaction = _make_interaction(user_id=42, guild_id=999)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset([42]),
            ALLOWED_GUILD_IDS=frozenset([111]),  # 999 not in list
            ALLOWED_ROLE_IDS=frozenset([501]),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is True

    async def test_unrestricted_public_mode_allows_anyone(self):
        interaction = _make_interaction(user_id=999, guild_id=None)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset(),
            ALLOWED_GUILD_IDS=frozenset(),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=False,
            REQUIRE_AUTH=False,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is True

    async def test_denial_sends_ephemeral_message_fresh_response(self):
        interaction = _make_interaction(user_id=999, guild_id=None, response_done=False)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset([1]),
            ALLOWED_GUILD_IDS=frozenset(),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is False
        interaction.response.send_message.assert_awaited_once()
        call_kwargs = interaction.response.send_message.call_args
        assert call_kwargs.kwargs.get("ephemeral") is True

    async def test_denial_uses_followup_when_response_already_done(self):
        interaction = _make_interaction(user_id=999, guild_id=None, response_done=True)
        with patch.multiple(
            bot_auth,
            ALLOWED_USER_IDS=frozenset([1]),
            ALLOWED_GUILD_IDS=frozenset(),
            ALLOWED_ROLE_IDS=frozenset(),
            _ANY_ALLOWLIST=True,
            REQUIRE_AUTH=True,
        ):
            result = await bot_auth.check_access(interaction)
        assert result is False
        interaction.followup.send.assert_awaited_once()
        call_kwargs = interaction.followup.send.call_args
        assert call_kwargs.kwargs.get("ephemeral") is True
