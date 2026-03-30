#!/bin/bash
##############################################################################
# MindForge Discord Bot Startup — Linux/macOS
##############################################################################
# Starts the Discord bot (requires Neo4j and other services)
#
# Prerequisites:
#   - DISCORD_TOKEN in .env file
#   - Discord application created and configured
#
# Usage: ./start-discord.sh
##############################################################################

echo ""
echo "[======================================================================]"
echo "MindForge Discord Bot Startup"
echo ""
echo "Starts the Discord bot integration"
echo ""
echo "Prerequisites:"
echo "  - DISCORD_TOKEN configured in .env"
echo "  - Discord application created and bot invited to server"
echo "  - Neo4j database with concept graph"
echo "  - ENABLE_TRACING=false or Langfuse running"
echo ""
echo "Starting services: Neo4j + Langfuse (optional)"
echo "[======================================================================]"

# Check if .env file exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found!"
    echo "Please copy env.example to .env and configure it."
    echo ""
    echo "Make sure DISCORD_TOKEN is set in .env"
    echo ""
    exit 1
fi

# Start services
echo "Starting Neo4j database..."
docker compose up -d neo4j langfuse-web langfuse-postgres langfuse-clickhouse langfuse-redis langfuse-minio langfuse-minio-init

sleep 3

echo ""
echo "Starting Discord bot..."
docker compose up --profile discord discord-bot
