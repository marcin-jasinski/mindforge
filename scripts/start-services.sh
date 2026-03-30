#!/bin/bash
##############################################################################
# MindForge Services Infrastructure Only — Linux/macOS
##############################################################################
# Starts supporting services (Docker infrastructure) without the application:
#   - Neo4j graph database (ports 7474, 7687)
#   - Langfuse observability (port 3100)
#   - Backing stores: Postgres, ClickHouse, Redis, MinIO
#
# Use this to run the app locally while keeping databases in Docker.
#
# Usage: ./start-services.sh [full|slim]
#        ./start-services.sh       # Both Neo4j and Langfuse
#        ./start-services.sh full  # Neo4j + full Langfuse stack
#        ./start-services.sh slim  # Neo4j only
##############################################################################

MODE="${1:-full}"

echo ""
echo "[======================================================================]"
echo "MindForge Services Infrastructure"

if [ "$MODE" = "full" ]; then
    echo ""
    echo "Services:"
    echo "  - Neo4j graph database        http://localhost:7474"
    echo "  - Langfuse Observability     http://localhost:3100"
    echo "  - MinIO Console              http://localhost:9090"
    echo ""
    echo "Run your app locally with Python while this manages infra"
fi

if [ "$MODE" = "slim" ]; then
    echo ""
    echo "Services:"
    echo "  - Neo4j graph database        http://localhost:7474"
    echo ""
    echo "Minimal setup for local development"
fi

echo "[======================================================================]"

# Start services based on mode
if [ "$MODE" = "full" ]; then
    docker compose up -d langfuse-web neo4j langfuse-postgres langfuse-clickhouse langfuse-redis langfuse-minio langfuse-minio-init
elif [ "$MODE" = "slim" ]; then
    docker compose up -d neo4j
else
    echo "ERROR: Invalid mode '$MODE'. Use 'full' or 'slim'"
    exit 1
fi

if [ $? -eq 0 ]; then
    echo ""
    echo "[OK] Services starting..."
    if [ "$MODE" = "full" ]; then
        echo ""
        echo "Access points:"
        echo "  - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)"
        echo "  - Langfuse:        http://localhost:3100"
        echo "  - MinIO Console:   http://localhost:9090 (user: minioadmin, pwd: minioadmin)"
        echo ""
        echo "Run your application locally:"
        echo "  ./start-dev.sh --with-db"
    elif [ "$MODE" = "slim" ]; then
        echo ""
        echo "Access points:"
        echo "  - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)"
        echo ""
        echo "Run your application locally:"
        echo "  ./start-dev.sh --with-db"
    fi
else
    echo "ERROR: Failed to start services"
    exit 1
fi
