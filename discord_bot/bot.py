#!/usr/bin/env python3
"""
MindForge Discord Bot — entry point.

Slash commands (implemented in cogs/):
  /quiz [lesson] [count]  — interactive quiz session powered by graph-RAG
  /search <query>         — concept and content search
  /upload <file>          — upload a lesson .md file to the processing queue

Daily scheduler (cogs/notifications.py):
  Sends spaced-repetition due reminders to a configured channel.

Run from the mindforge/ directory:
  python -m discord_bot.bot
  python discord_bot/bot.py
"""
from __future__ import annotations

import asyncio
import logging
import os
import sys
from pathlib import Path

import discord
from discord.ext import commands
from dotenv import load_dotenv

# Ensure the mindforge root is on sys.path regardless of how this file is
# invoked (direct script, -m module, or Docker entrypoint).
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

log = logging.getLogger(__name__)

_COGS = [
    "discord_bot.cogs.quiz",
    "discord_bot.cogs.search",
    "discord_bot.cogs.upload",
    "discord_bot.cogs.notifications",
]


class MindForgeBot(commands.Bot):
    """Main Discord bot for MindForge."""

    def __init__(self) -> None:
        intents = discord.Intents.default()
        # message_content is a privileged intent — MindForge uses only slash
        # commands and does not inspect message text, so this is not needed.
        super().__init__(command_prefix=commands.when_mentioned, intents=intents)
        # Set in setup_hook after loading config
        self.config = None       # processor.llm_client.Config
        self.async_llm = None   # processor.llm_client.AsyncLLMClient
        self.neo4j_driver = None  # neo4j.Driver | None

    async def setup_hook(self) -> None:
        """Called once when the bot is starting up, before connecting to Discord."""
        # Load processor configuration (reads .env via dotenv)
        from processor.llm_client import load_config, AsyncLLMClient
        from processor.settings import load_settings
        from processor.llm_client import load_credentials
        from processor import tracing

        settings = load_settings(ROOT)
        creds = load_credentials(ROOT)

        # Initialize tracing explicitly so it only runs once
        tracing.init_tracing(settings, creds)

        self.config = load_config(ROOT)
        log.info("Config loaded: model_large=%s", self.config.model_large)

        # Async LLM client for use in cog handlers (non-blocking)
        self.async_llm = AsyncLLMClient(
            base_url=creds.base_url,
            api_key=creds.api_key,
            default_headers=creds.extra_headers,
        )
        log.info("AsyncLLMClient ready")

        # Validate access-control config — raises RuntimeError if unsafe
        from discord_bot.bot_auth import validate_auth_config, ALLOWED_GUILD_IDS

        validate_auth_config()

        # Connect to Neo4j when graph-RAG is enabled
        if self.config.enable_graph_rag:
            from processor.tools.graph_rag import GraphConfig, connect

            graph_cfg = GraphConfig(
                uri=self.config.neo4j_uri,
                username=self.config.neo4j_username,
                password=self.config.neo4j_password,
            )
            try:
                self.neo4j_driver = connect(graph_cfg)
                log.info("Connected to Neo4j: %s", self.config.neo4j_uri)
            except Exception as exc:
                log.error("Failed to connect to Neo4j: %s — quiz/search unavailable", exc)
        else:
            log.warning(
                "ENABLE_GRAPH_RAG=false — /quiz and /search commands will report an error."
            )

        # Load all cogs
        for cog_path in _COGS:
            try:
                await self.load_extension(cog_path)
                log.info("Loaded cog: %s", cog_path)
            except Exception as exc:
                log.error("Failed to load cog %s: %s", cog_path, exc)

        # Sync slash commands to the configured guilds only.
        # Global sync (no guild arg) is skipped by default to avoid exposing
        # commands to unintended servers while allowlists are in effect.
        if ALLOWED_GUILD_IDS:
            guild_objects = [discord.Object(id=gid) for gid in ALLOWED_GUILD_IDS]
            for guild_obj in guild_objects:
                self.tree.copy_global_to(guild=guild_obj)
                synced = await self.tree.sync(guild=guild_obj)
                log.info(
                    "Synced %d slash command(s) to guild %s.", len(synced), guild_obj.id
                )
        else:
            # No guild allowlist — sync globally (public / unrestricted mode)
            synced = await self.tree.sync()
            log.info("Synced %d slash command(s) globally.", len(synced))

    async def on_ready(self) -> None:
        log.info(
            "MindForge Bot ready — logged in as %s (ID: %s)",
            self.user,
            self.user.id,
        )
        await self.change_presence(
            activity=discord.Activity(
                type=discord.ActivityType.watching,
                name="lekcje | /quiz /search /upload",
            )
        )

    async def close(self) -> None:
        if self.neo4j_driver is not None:
            try:
                self.neo4j_driver.close()
                log.info("Neo4j driver closed.")
            except Exception:
                pass
        await super().close()


async def main() -> None:
    """Main entry point."""
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)-7s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    load_dotenv(ROOT / ".env")

    token = os.environ.get("DISCORD_BOT_TOKEN", "").strip()
    if not token:
        raise ValueError(
            "DISCORD_BOT_TOKEN is not set. "
            "Add it to your .env file or pass it as an environment variable."
        )

    bot = MindForgeBot()
    async with bot:
        await bot.start(token)


if __name__ == "__main__":
    asyncio.run(main())
