"""
Upload cog — /upload <file> slash command.

Accepts a .md attachment and saves it into the mindforge new/ directory so the
pipeline (mindforge.py --watch) picks it up automatically.

Security measures applied:
  • Path traversal prevention — filename is sanitised via the shared
    processor.tools.upload_sanitize module which handles both POSIX and
    Windows separator styles, drive letters, and dot-only names, and
    verifies the final resolved path stays inside new/.
  • Extension whitelist — only .md files are accepted
  • File size limit — configurable via UPLOAD_MAX_SIZE_MB env var (default 5 MB)
  • Content encoding validation — file must be valid UTF-8 text
"""
from __future__ import annotations

import logging
import os
import sys
from pathlib import Path
from typing import TYPE_CHECKING

import discord
from discord import app_commands
from discord.ext import commands

ROOT = Path(__file__).resolve().parent.parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from processor.tools.upload_sanitize import sanitize_upload_filename, unique_upload_dest
from discord_bot.bot_auth import check_access

if TYPE_CHECKING:
    from discord_bot.bot import MindForgeBot

log = logging.getLogger(__name__)

_DEFAULT_MAX_MB = 5
_ALLOWED_EXTENSIONS = {".md"}


class UploadCog(commands.Cog, name="Upload"):
    """Upload a Markdown lesson file to the processing queue."""

    def __init__(self, bot: "MindForgeBot") -> None:
        self.bot = bot
        max_mb_str = os.environ.get("UPLOAD_MAX_SIZE_MB", str(_DEFAULT_MAX_MB))
        try:
            self._max_bytes = int(float(max_mb_str)) * 1024 * 1024
        except ValueError:
            self._max_bytes = _DEFAULT_MAX_MB * 1024 * 1024

    @app_commands.command(
        name="upload",
        description="Prześlij plik .md z treścią lekcji do kolejki przetwarzania",
    )
    @app_commands.describe(
        file="Plik Markdown (.md) z treścią lekcji (maks. 5 MB)"
    )
    async def upload(
        self,
        interaction: discord.Interaction,
        file: discord.Attachment,
    ) -> None:
        if not await check_access(interaction):
            return
        # ── Validate filename / extension ──────────────────────────
        try:
            safe_name = sanitize_upload_filename(file.filename)
        except ValueError as exc:
            await interaction.response.send_message(
                f"❌ Nieprawidłowa nazwa pliku: {exc}",
                ephemeral=True,
            )
            return

        ext = Path(safe_name).suffix.lower()

        if ext not in _ALLOWED_EXTENSIONS:
            await interaction.response.send_message(
                f"❌ Tylko pliki `.md` są akceptowane (otrzymano: `{ext or 'brak rozszerzenia'}`).",
                ephemeral=True,
            )
            return

        # ── Validate file size ─────────────────────────────────────
        if file.size > self._max_bytes:
            size_kb = file.size // 1024
            max_kb = self._max_bytes // 1024
            await interaction.response.send_message(
                f"❌ Plik jest za duży ({size_kb} KB). Maksymalny dozwolony rozmiar to {max_kb} KB.",
                ephemeral=True,
            )
            return

        await interaction.response.defer(thinking=True)

        # ── Download and decode ────────────────────────────────────
        try:
            raw_bytes = await file.read()
        except discord.HTTPException as exc:
            log.error("Failed to download attachment %r: %s", safe_name, exc)
            await interaction.followup.send(
                "❌ Nie udało się pobrać pliku z Discord.",
                ephemeral=True,
            )
            return

        try:
            raw_bytes.decode("utf-8")
        except UnicodeDecodeError:
            await interaction.followup.send(
                "❌ Plik nie jest poprawnym UTF-8. Upewnij się, że to plik tekstowy Markdown.",
                ephemeral=True,
            )
            return

        # ── Write to new/ ──────────────────────────────────────────
        new_dir = ROOT / "new"
        new_dir.mkdir(parents=True, exist_ok=True)

        try:
            dest = unique_upload_dest(new_dir, safe_name)
            dest.write_bytes(raw_bytes)
        except (ValueError, RuntimeError, OSError) as exc:
            log.error("Failed to write %r: %s", safe_name, exc)
            await interaction.followup.send(
                "❌ Błąd zapisu pliku na serwerze. Spróbuj ponownie.",
                ephemeral=True,
            )
            return

        log.info(
            "Saved upload from %s#%s → %s (%d bytes)",
            interaction.user.name,
            interaction.user.discriminator,
            dest,
            len(raw_bytes),
        )

        embed = discord.Embed(
            title="✅ Plik przesłany do kolejki",
            color=discord.Color.green(),
        )
        embed.add_field(name="Nazwa pliku", value=f"`{dest.name}`", inline=True)
        embed.add_field(
            name="Rozmiar",
            value=f"{len(raw_bytes) // 1024} KB",
            inline=True,
        )
        embed.add_field(
            name="Co dalej?",
            value=(
                "Pipeline przetworzy plik automatycznie (tryb `--watch`) "
                "lub przy następnym ręcznym uruchomieniu `python mindforge.py --once`."
            ),
            inline=False,
        )
        embed.set_footer(text=f"Przesłał: {interaction.user.display_name}")
        await interaction.followup.send(embed=embed)


async def setup(bot: commands.Bot) -> None:
    await bot.add_cog(UploadCog(bot))  # type: ignore[arg-type]
