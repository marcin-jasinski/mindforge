"""
Search cog — /search <query> slash command.

Calls graph_rag.retrieve() and presents paginated results as Discord embeds:
  • Related concepts with definitions
  • Lesson chunks (3 per page, with Prev/Next navigation)
  • Key facts (shown on first page)
  • Source lessons in the footer
"""
from __future__ import annotations

import asyncio
import logging
import sys
from pathlib import Path
from typing import TYPE_CHECKING, Any

import discord
from discord import app_commands
from discord.ext import commands

ROOT = Path(__file__).resolve().parent.parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from discord_bot.bot_auth import check_access

if TYPE_CHECKING:
    from discord_bot.bot import MindForgeBot

log = logging.getLogger(__name__)

CHUNKS_PER_PAGE = 3
MAX_CONCEPT_DEFINITION = 150
MAX_CHUNK_TEXT = 350
MAX_FACT_TEXT = 200

_OWNERSHIP_DENIAL = (
    "❌ Tylko osoba, która uruchomiła to zapytanie, może przeglądać wyniki."
)


# ── Paginated view ───────────────────────────────────────────────────


class SearchView(discord.ui.View):
    """Paginated view for search results (navigates through lesson chunks)."""

    def __init__(self, result: Any, query: str, user_id: int) -> None:
        super().__init__(timeout=180)
        self.result = result
        self.query = query
        self.user_id = user_id
        self.page = 0
        self.total_pages = max(
            1,
            (len(result.chunks) + CHUNKS_PER_PAGE - 1) // CHUNKS_PER_PAGE,
        )

        # Add navigation buttons programmatically so each instance has its own
        self._prev = discord.ui.Button(
            label="◀ Poprzednia",
            style=discord.ButtonStyle.secondary,
            disabled=True,  # Disabled on the first page
        )
        self._prev.callback = self._prev_callback
        self.add_item(self._prev)

        self._next = discord.ui.Button(
            label="Następna ▶",
            style=discord.ButtonStyle.secondary,
            disabled=self.total_pages <= 1,
        )
        self._next.callback = self._next_callback
        self.add_item(self._next)

    # ── Callbacks ────────────────────────────────────────────────────

    async def _prev_callback(self, interaction: discord.Interaction) -> None:
        if interaction.user.id != self.user_id:
            await interaction.response.send_message(_OWNERSHIP_DENIAL, ephemeral=True)
            return
        self.page -= 1
        self._refresh_buttons()
        await interaction.response.edit_message(embed=self.build_embed(), view=self)

    async def _next_callback(self, interaction: discord.Interaction) -> None:
        if interaction.user.id != self.user_id:
            await interaction.response.send_message(_OWNERSHIP_DENIAL, ephemeral=True)
            return
        self.page += 1
        self._refresh_buttons()
        await interaction.response.edit_message(embed=self.build_embed(), view=self)

    # ── Helpers ──────────────────────────────────────────────────────

    def _refresh_buttons(self) -> None:
        self._prev.disabled = self.page <= 0
        self._next.disabled = self.page >= self.total_pages - 1

    def build_embed(self) -> discord.Embed:
        embed = discord.Embed(
            title=f"🔍 Wyniki dla: {self.query[:100]}",
            color=discord.Color.blue(),
        )

        # Related concepts — always shown on every page
        if self.result.concepts:
            lines = []
            for c in self.result.concepts[:5]:
                defn = c.get("definition", "")[:MAX_CONCEPT_DEFINITION]
                lines.append(f"**{c['name']}**: {defn}" if defn else f"**{c['name']}**")
            embed.add_field(
                name="📚 Powiązane pojęcia",
                value="\n".join(lines),
                inline=False,
            )

        # Lesson chunks for the current page
        start = self.page * CHUNKS_PER_PAGE
        page_chunks = self.result.chunks[start : start + CHUNKS_PER_PAGE]
        if page_chunks:
            parts = []
            for chunk in page_chunks:
                lesson = chunk.get("lesson_number", "?")
                text = chunk.get("text", "")[:MAX_CHUNK_TEXT]
                parts.append(f"[**{lesson}**] {text}")
            embed.add_field(
                name=f"📝 Fragmenty lekcji (strona {self.page + 1}/{self.total_pages})",
                value="\n\n".join(parts),
                inline=False,
            )
        elif not self.result.concepts:
            embed.description = "Nie znaleziono żadnych wyników."

        # Key facts — only on the first page
        if self.page == 0 and self.result.facts:
            fact_lines = [f"• {f[:MAX_FACT_TEXT]}" for f in self.result.facts[:5]]
            embed.add_field(
                name="💡 Kluczowe fakty",
                value="\n".join(fact_lines),
                inline=False,
            )

        if self.result.source_lessons:
            embed.set_footer(text="Źródła: " + ", ".join(self.result.source_lessons))

        return embed


# ── Cog ─────────────────────────────────────────────────────────────


class SearchCog(commands.Cog, name="Search"):
    """Content and concept search powered by graph-RAG."""

    def __init__(self, bot: "MindForgeBot") -> None:
        self.bot = bot

    @app_commands.command(
        name="search",
        description="Przeszukaj grafy wiedzy i fragmenty lekcji",
    )
    @app_commands.describe(query="Zapytanie lub temat do wyszukania")
    async def search(
        self,
        interaction: discord.Interaction,
        query: str,
    ) -> None:
        if not await check_access(interaction):
            return
        if self.bot.neo4j_driver is None:
            await interaction.response.send_message(
                "❌ Graph-RAG nie jest dostępny. "
                "Ustaw `ENABLE_GRAPH_RAG=true` w `.env` i uruchom Neo4j.",
                ephemeral=True,
            )
            return

        if not query.strip():
            await interaction.response.send_message(
                "❌ Podaj treść zapytania.",
                ephemeral=True,
            )
            return

        await interaction.response.defer(thinking=True)

        try:
            from processor.tools.graph_rag import retrieve

            result = await asyncio.to_thread(
                retrieve,
                self.bot.neo4j_driver,
                query.strip(),
                max_results=10,
            )
        except Exception as exc:
            log.error("Search failed for query %r: %s", query, exc, exc_info=True)
            await interaction.followup.send(
                "❌ Wyszukiwanie nie powiodło się. Spróbuj ponownie.",
                ephemeral=True,
            )
            return

        if not result.chunks and not result.concepts and not result.facts:
            await interaction.followup.send(
                f"❌ Brak wyników dla zapytania: **{query[:100]}**\n"
                "Spróbuj innego sformułowania lub zaindeksuj więcej lekcji.",
                ephemeral=False,
            )
            return

        view = SearchView(result, query.strip(), interaction.user.id)
        await interaction.followup.send(embed=view.build_embed(), view=view)


async def setup(bot: commands.Bot) -> None:
    await bot.add_cog(SearchCog(bot))  # type: ignore[arg-type]
