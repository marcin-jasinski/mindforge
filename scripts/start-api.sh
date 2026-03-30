#!/bin/bash
##############################################################################
# MindForge API Only Startup — Linux/macOS
##############################################################################
# Starts just the FastAPI backend + Angular frontend (+ Neo4j for graph RAG)
#
# Usage: ./start-api.sh
##############################################################################

echo ""
echo "[======================================================================]"
echo "MindForge API Startup"
echo ""
echo "Services:"
echo "  - API (FastAPI) + Frontend    http://localhost:8080"
echo "  - Neo4j graph database        http://localhost:7474"
echo ""
echo "Note: Langfuse observability is NOT started (ENABLE_TRACING...)"
echo "[======================================================================]"

# Check if .env file exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found!"
    echo "Please copy env.example to .env and configure it."
    echo ""
    exit 1
fi

# Check Docker Compose version
if ! docker compose --version &>/dev/null; then
    echo "ERROR: Docker Compose not found"
    echo "Install Docker Desktop from: https://www.docker.com/products/docker-desktop"
    echo ""
    exit 1
fi

# Start Docker Compose with required services
# Note: Using service names directly (compatible with older Docker Compose versions)
docker compose up -d api neo4j

if [ $? -eq 0 ]; then
    echo ""
    echo "[OK] Starting services..."
    echo ""
    echo "Accessing services:"
    echo "  - API + Frontend:  http://localhost:8080"
    echo "  - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)"
else
    echo "ERROR: Failed to start Docker Compose"
    exit 1
fi
