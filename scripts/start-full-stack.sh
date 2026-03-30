#!/bin/bash
##############################################################################
# MindForge Full Stack Startup — Linux/macOS
##############################################################################
# Starts the complete stack:
#   - FastAPI backend + Angular frontend (port 8080)
#   - Neo4j graph database (ports 7474, 7687)
#   - Langfuse observability (port 3100)
#   - All supporting infrastructure (Postgres, ClickHouse, Redis, MinIO)
#
# Usage: ./start-full-stack.sh
##############################################################################

echo ""
echo "[======================================================================]"
echo "MindForge Full Stack Startup"
echo ""
echo "Services:"
echo "  - API (FastAPI)              http://localhost:8080"
echo "  - Neo4j Browser              http://localhost:7474"
echo "  - Langfuse Observability     http://localhost:3100"
echo "  - MinIO Console              http://localhost:9090"
echo ""
echo "Waiting for services to be healthy... (may take 30-60 seconds)"
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
docker compose up -d api langfuse-web neo4j langfuse-postgres langfuse-clickhouse langfuse-redis langfuse-minio langfuse-minio-init

if [ $? -eq 0 ]; then
    echo ""
    echo "[OK] Starting services... Please wait for all containers to be healthy."
    echo ""
    echo "Accessing services:"
    echo "  - API + Frontend:  http://localhost:8080"
    echo "  - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)"
    echo "  - Langfuse:        http://localhost:3100"
    echo "  - MinIO Console:   http://localhost:9090 (user: minioadmin, pwd: minioadmin)"
else
    echo "ERROR: Failed to start Docker Compose"
    exit 1
fi
