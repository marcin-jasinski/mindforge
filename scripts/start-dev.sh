#!/bin/bash
##############################################################################
# MindForge Development Mode Startup — Linux/macOS
##############################################################################
# Starts the Python development environment with:
#   - Virtual environment activation
#   - Markdown summarizer in watch mode
#   - Neo4j database (optional)
#
# Prerequisites:
#   - Python 3.9+ with venv created: python3 -m venv venv
#   - Dependencies installed: pip install -r requirements.txt
#
# Usage: ./start-dev.sh [--with-db]
#        ./start-dev.sh              # Watch mode only
#        ./start-dev.sh --with-db    # Watch mode + Neo4j
##############################################################################

# Function to cleanup on exit
cleanup() {
    echo ""
    echo "Stopping development mode..."
    deactivate 2>/dev/null || true
}
trap cleanup EXIT

# Detect virtual environment location
VENV_DIR="venv"
if [ -d ".venv" ]; then
    VENV_DIR=".venv"
fi

# Check if venv exists
if [ ! -d "$VENV_DIR" ]; then
    echo ""
    echo "ERROR: Virtual environment not found at '$VENV_DIR'"
    echo ""
    echo "Please create it with:"
    echo "  python3 -m venv venv"
    echo "  source venv/bin/activate"
    echo "  pip install -r requirements.txt"
    echo ""
    exit 1
fi

echo ""
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║            MindForge Development Mode                            ║"
echo "║                                                                  ║"
echo "║ Running in local Python environment (watch mode)                 ║"
echo "║ Watching: new/ folder for markdown files                         ║"
echo "║ Output: summarized/, flashcards/, diagrams/, knowledge/          ║"
echo "║                                                                  ║"
if [ "$1" == "--with-db" ]; then
    echo "║ Extras: Neo4j database running on localhost:7687                ║"
    echo "║         (Browser: http://localhost:7474)                        ║"
else
    echo "║ Tip: Use './start-dev.sh --with-db' to also start Neo4j         ║"
fi
echo "║                                                                  ║"
echo "║ Press Ctrl+C to stop    (logs will show all pipeline activity)  ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""

# Check if .env file exists
if [ ! -f .env ]; then
    echo "ERROR: .env file not found!"
    echo "Please copy env.example to .env and configure it."
    echo ""
    exit 1
fi

# Start Neo4j if requested
if [ "$1" == "--with-db" ]; then
    echo "Starting Neo4j database..."
    docker compose up -d neo4j
    sleep 2
fi

# Activate venv and run the summarizer
source "$VENV_DIR/bin/activate"

echo ""
echo "[OK] Virtual environment activated"
echo "[OK] Starting markdown summarizer in watch mode..."

python mindforge.py --watch
