#!/bin/bash
##############################################################################
# MindForge Stop All Containers — Linux/macOS
##############################################################################
# Stops all running MindForge Docker containers
#
# Usage: ./stop.sh [--clean]
#        ./stop.sh         # Stop containers (keep volumes)
#        ./stop.sh --clean # Stop and remove containers (keep volumes)
##############################################################################

CLEAN="$1"

echo ""
echo "[======================================================================]"
if [ "$CLEAN" = "--clean" ]; then
    echo "Stopping and Removing Containers"
else
    echo "Stopping All Containers"
fi
echo "[======================================================================]"

if [ "$CLEAN" = "--clean" ]; then
    echo "Removing containers..."
    docker compose down --remove-orphans
else
    echo "Stopping containers..."
    docker compose stop
fi

if [ $? -eq 0 ]; then
    echo ""
    echo "[OK] Done"
    if [ "$CLEAN" = "--clean" ]; then
        echo ""
        echo "To fully reset, also run:"
        echo "  docker compose down -v    # Remove volumes as well"
    fi
else
    echo "ERROR: Failed to stop containers"
    exit 1
fi
