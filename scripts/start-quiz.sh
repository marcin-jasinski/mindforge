#!/bin/bash
##############################################################################
# MindForge Quiz Agent Startup — Linux/macOS
##############################################################################
# Starts the interactive quiz agent (requires Neo4j and other services)
#
# Usage: ./start-quiz.sh
##############################################################################

echo ""
echo "[======================================================================]"
echo "MindForge Quiz Agent Startup"
echo ""
echo "Starts the interactive assessment runner (powered by graph-RAG)"
echo ""
echo "Prerequisites:"
echo "  - Neo4j database with concept graph populated"
echo "  - ENABLE_TRACING=false or Langfuse running"
echo ""
echo "Starting services: Neo4j + Langfuse (optional)"
echo "[======================================================================]"

# Check if .env file exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found!"
    echo "Please copy env.example to .env and configure it."
    echo ""
    exit 1
fi

# Start services
echo "Starting Neo4j database..."
docker compose up -d neo4j langfuse-web langfuse-postgres langfuse-clickhouse langfuse-redis langfuse-minio langfuse-minio-init

sleep 3

# Start quiz agent
docker compose run --rm quiz
