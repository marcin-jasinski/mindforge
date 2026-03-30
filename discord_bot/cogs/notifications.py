"""
Notifications cog — daily spaced-repetition (SR) due reminders.

A background task runs once a day at a configurable UTC hour (default 08:00).
It reads state/sr_state.json, counts cards whose due_date is today or earlier,
and posts a reminder embed to the Discord channel set in DISCORD_NOTIFICATION_CHANNEL_ID.

If the channel ID is not set the task is effectively a no-op (logged once at startup).

Environment variables:
  DISCORD_NOTIFICATION_CHANNEL_ID — target channel (integer snowflake)
  NOTIFICATION_HOUR_UTC            — UTC hour for daily check (0–23, default 8)
"""
from __future__ import annotations

import datetime
import json
import logging
import os
import sys
from pathlib import Path
from typing import TYPE_CHECKING

import discord
from discord.ext import commands, tasks

ROOT = Path(__file__).resolve().parent.parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

if TYPE_CHECKING:
    from discord_bot.bot import MindForgeBot

log = logging.getLogger(__name__)

SR_STATE_PATH = ROOT / "state" / "sr_state.json"


def _load_due_count(today: datetime.date) -> int:
    """Return the number of flashcards due on *today* or earlier."""
    if not SR_STATE_PATH.exists():
        return 0
    try:
        data: dict = json.loads(SR_STATE_PATH.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as exc:
        log.warning("Could not read sr_state.json: %s", exc)
        return 0

    due_count = 0
    today_iso = today.isoformat()
    for card in data.values():
        due_date: str = card.get("due_date", "")
        # Cards with no due_date are always due; cards with a past/today date are due
        if not due_date or due_date <= today_iso:
            due_count += 1
    return due_count


def _parse_notification_hour() -> int:
    """Return the configured UTC hour for the daily notification (default 8)."""
    try:
        hour = int(os.environ.get("NOTIFICATION_HOUR_UTC", "8"))
        return max(0, min(23, hour))
    except ValueError:
        return 8


class NotificationsCog(commands.Cog, name="Notifications"):
    """Daily spaced-repetition reminder via Discord channel message."""

    def __init__(self, bot: "MindForgeBot") -> None:
        self.bot = bot
        self._channel_id: int = 0
        raw = os.environ.get("DISCORD_NOTIFICATION_CHANNEL_ID", "").strip()
        if raw:
            try:
                self._channel_id = int(raw)
            except ValueError:
                log.warning(
                    "DISCORD_NOTIFICATION_CHANNEL_ID=%r is not a valid integer — "
                    "SR notifications disabled.",
                    raw,
                )

        hour = _parse_notification_hour()
        # Reconfigure the loop time based on env var
        self.daily_reminder.change_interval(
            time=datetime.time(hour=hour, minute=0, tzinfo=datetime.timezone.utc)
        )
        self.daily_reminder.start()

    def cog_unload(self) -> None:
        self.daily_reminder.cancel()

    # ── Background task ──────────────────────────────────────────────

    @tasks.loop(time=datetime.time(hour=8, minute=0, tzinfo=datetime.timezone.utc))
    async def daily_reminder(self) -> None:
        """Check for due flashcards and post a reminder if any are found."""
        if not self._channel_id:
            return

        today = datetime.date.today()
        due_count = _load_due_count(today)

        if due_count == 0:
            log.debug("SR check: no cards due on %s.", today)
            return

        channel = self.bot.get_channel(self._channel_id)
        if channel is None:
            try:
                channel = await self.bot.fetch_channel(self._channel_id)
            except (discord.NotFound, discord.Forbidden) as exc:
                log.error(
                    "Cannot access notification channel %d: %s",
                    self._channel_id,
                    exc,
                )
                return

        embed = discord.Embed(
            title="📚 Fiszki do powtórki",
            description=(
                f"Masz dziś **{due_count}** "
                + ("fiszkę" if due_count == 1 else "fiszek")
                + " do powtórki!"
            ),
            color=discord.Color.gold(),
            timestamp=datetime.datetime.now(datetime.timezone.utc),
        )
        embed.add_field(
            name="Jak powtórzyć?",
            value=(
                "Otwórz aplikację MindForge GUI (http://localhost:8080) "
                "i przejdź do sekcji **Fiszki**."
            ),
            inline=False,
        )
        embed.set_footer(text=f"Powtórka SR • {today}")

        try:
            await channel.send(embed=embed)  # type: ignore[union-attr]
            log.info(
                "SR reminder sent to channel %d: %d card(s) due.",
                self._channel_id,
                due_count,
            )
        except discord.Forbidden:
            log.error(
                "Missing permission to send messages to channel %d.",
                self._channel_id,
            )

    @daily_reminder.before_loop
    async def _before_daily_reminder(self) -> None:
        """Wait until the bot is fully ready before starting the task."""
        await self.bot.wait_until_ready()
        if self._channel_id:
            log.info(
                "SR notification scheduler active — channel %d, daily at %02d:00 UTC.",
                self._channel_id,
                _parse_notification_hour(),
            )
        else:
            log.info(
                "DISCORD_NOTIFICATION_CHANNEL_ID not set — SR notifications disabled."
            )


async def setup(bot: commands.Bot) -> None:
    await bot.add_cog(NotificationsCog(bot))  # type: ignore[arg-type]
