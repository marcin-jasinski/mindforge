# MindForge Startup Scripts Guide

This directory contains convenient startup scripts for running MindForge in different configurations. Each preset combines different services and is optimized for specific development workflows.

## Quick Start

### Windows
```powershell
# Full stack with all services (includes frontend)
start-full-stack.bat

# API + Frontend only (minimal setup)
start-api.bat

# Development mode (local Python with file watcher)
start-dev.bat

# Services only (Docker infrastructure for local Python dev)
start-services.bat

# Stop all services
stop.bat
```

### Linux / macOS
```bash
# Full stack with all services (includes frontend)
./start-full-stack.sh

# API + Frontend only (minimal setup)
./start-api.sh

# Development mode (local Python with file watcher)
./start-dev.sh

# Services only (Docker infrastructure for local Python dev)
./start-services.sh

# Stop all services
./stop.sh
```

---

## Configuration Profiles

### 1. **Full Stack** (`start-full-stack`)
**Best for:** Testing the complete system end-to-end, demoing, or running everything in Docker.

**Services:**
- **API with Frontend** (FastAPI REST + Angular SPA) → `http://localhost:8080`
  - Frontend is automatically built and served by the API
  - Includes REST API endpoints for quiz, flashcards, search, concepts
- **Neo4j** Graph Database → `http://localhost:7474` (user: `neo4j`, pwd: `password`)
- **Langfuse** Observability Platform → `http://localhost:3100`
- **MinIO** Object Storage Console → `http://localhost:9090`
- Supporting infrastructure: PostgreSQL, ClickHouse, Redis

**When to use:**
- ✅ You want everything in Docker (nothing running locally)
- ✅ You're testing the full pipeline end-to-end
- ✅ You're demoing the system
- ✅ You want built-in observability (LLM call tracking, costs)

**Time to ready:** ~60-90 seconds

```bash
# Windows
start-full-stack.bat

# macOS/Linux
./start-full-stack.sh
```

---

### 2. **API Only** (`start-api`)
**Best for:** Running the web interface and REST API with minimal dependencies.

**Services:**
- **API with Frontend** (FastAPI + Angular SPA) → `http://localhost:8080`
  - Frontend is automatically built and served by the API
- **Neo4j** Graph Database → `http://localhost:7474`

**When to use:**
- ✅ You want a lightweight setup for frontend and API testing
- ✅ You're running the Python processor locally (separate `start-dev`)
- ✅ You don't need observability yet
- ✅ You want fast startup with just the essentials
- ✅ You want to test the web interface without overhead

**Time to ready:** ~30-45 seconds

```bash
# Windows
start-api.bat

# macOS/Linux
./start-api.sh
```

---

### 3. **Development Mode** (`start-dev`)
**Best for:** Active development with Python-based markdown processor and file watcher.

**Services:**
- **Python Markdown Summarizer** (local, watch mode)
  - Watches: `new/` folder
  - Outputs: `summarized/`, `flashcards/`, `diagrams/`, `knowledge/`
- **Neo4j** (optional, add `--with-db` flag)

**When to use:**
- ✅ You're developing the pipeline (summarizer, flashcard gen, etc.)
- ✅ You want hot-reload file watching
- ✅ You're debugging LLM prompts and processors
- ✅ You want full local control without Docker overhead
- ✅ You like seeing pipeline logs in real-time

**Prerequisites:**
- Python 3.9+ with virtual environment created
- `pip install -r requirements.txt` already run

**Time to ready:** ~5 seconds

```bash
# Windows
start-dev.bat                 # Watch mode only
start-dev.bat --with-db       # Watch mode + Neo4j

# macOS/Linux
./start-dev.sh                 # Watch mode only
./start-dev.sh --with-db       # Watch mode + Neo4j
```

**Workflow Example:**
```bash
# Terminal 1: Run development mode
start-dev.bat --with-db

# Terminal 2: Drop markdown files into new/ folder, watch console for output
# Files process within seconds, outputs appear in summarized/, flashcards/, etc.
```

---

### 4. **Services Infrastructure Only** (`start-services`)
**Best for:** Running Docker infrastructure while developing Python code locally.

**Services:**
- **Neo4j** Graph Database → `http://localhost:7474`
- **Langfuse** Observability (optional: `full` vs `slim` mode)
- Supporting infrastructure: PostgreSQL, ClickHouse, Redis, MinIO

**When to use:**
- ✅ You want to develop Python code locally (without Docker Python)
- ✅ You want databases in Docker but control over Python execution
- ✅ You're debugging or profiling Python directly
- ✅ You want IDE breakpoints to work

**Modes:**

```bash
# Windows
start-services.bat            # Neo4j + Langfuse
start-services.bat slim       # Neo4j only

# macOS/Linux
./start-services.sh            # Neo4j + Langfuse
./start-services.sh slim       # Neo4j only
```

**Example Workflow:**
```bash
# Terminal 1: Start infrastructure
start-services.bat full

# Terminal 2: Activate venv and run app locally
venv\Scripts\activate
python mindforge.py --watch
```

---

### 5. **Quiz Agent** (`start-quiz`)
**Best for:** Interactive testing and running the assessment engine.

**Services:**
- **Neo4j** Graph Database (must be populated with concepts)
- **Langfuse** Observability (optional)

**When to use:**
- ✅ You want to run interactive quizzes
- ✅ You want to test the graph-RAG question generation
- ✅ You've already processed lessons and populated the graph

```bash
# Windows
start-quiz.bat

# macOS/Linux
./start-quiz.sh
```

---

### 6. **Discord Bot** (`start-discord`)
**Best for:** Running the Discord bot integration.

**Services:**
- **Neo4j** Graph Database
- **Langfuse** Observability (optional)

**Prerequisites:**
- `DISCORD_TOKEN` set in `.env`
- Discord application created and bot invited to server

**When to use:**
- ✅ You're testing Discord bot functionality
- ✅ You want Discord users to access MindForge

```bash
# Windows
start-discord.bat

# macOS/Linux
./start-discord.sh
```

---

## Stopping Services

### Stop (keep containers and volumes)
```bash
# Windows
stop.bat

# macOS/Linux
./stop.sh
```

### Stop and remove containers (but keep volumes)
```bash
# Windows
stop.bat --clean

# macOS/Linux
./stop.sh --clean
```

### Full cleanup (stop, remove containers AND volumes)
```bash
docker compose down -v
```

---

## Common Workflows

### Workflow A: Pure Local Development (No Docker)
```bash
# Terminal 1: Set up services
start-services.bat slim

# Terminal 2: Run local Python with watcher
start-dev.bat --with-db
```

### Workflow B: Full Docker (Everything in containers)
```bash
start-full-stack.bat
# Access API at http://localhost:8080
```

### Workflow C: API Testing with Local Pipeline
```bash
# Terminal 1: Start API
start-api.bat

# Terminal 2: Run processor locally
start-dev.bat --with-db

# Terminal 3: (optional) Monitor with Langfuse
# Access Langfuse at http://localhost:3100 after enabling ENABLE_TRACING=true
```

### Workflow D: Development with IDE Debugging
```bash
# Terminal 1: Just Neo4j (minimal Docker)
start-services.bat slim

# Terminal 2: IDE – Run/Debug mindforge.py with breakpoints
# (From your IDE, e.g., VS Code Python Debug)
```

---

## Environment Setup

Before running scripts, ensure:

1. **Copy and configure `.env`:**
   ```bash
   cp env.example .env
   # Edit .env and add your API keys
   ```

2. **For development mode, set up Python venv:**
   ```bash
   python -m venv venv
   venv\Scripts\activate          # Windows
   source venv/bin/activate       # macOS/Linux
   pip install -r requirements.txt
   ```

3. **Make shell scripts executable (macOS/Linux):**
   ```bash
   chmod +x start-*.sh stop.sh
   ```

4. **Ensure Docker is running:**
   ```bash
   docker --version
   docker compose --version
   ```

---

## Troubleshooting

### "ERROR: .env file not found"
Copy `env.example` to `.env` and fill in required API keys.

### "Virtual environment not found"
Create it with:
```bash
python -m venv venv
venv\Scripts\activate          # Windows
source venv/bin/activate       # macOS/Linux
pip install -r requirements.txt
```

### Containers stuck in "starting" state
Check Docker daemon:
```bash
docker ps
docker logs mindforge-api       # Check specific service logs
```

### Port already in use (e.g., 8080)
Kill the process using the port:
```bash
# Windows (from admin shell)
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# macOS/Linux
lsof -i :8080
kill -9 <PID>
```

### Neo4j won't connect
Verify Neo4j is healthy:
```bash
docker compose ps neo4j
docker logs mindforge-neo4j
```

Default credentials: `neo4j` / `password`

### Docker Compose errors
Update Docker and Docker Compose to latest versions:
```bash
docker --version          # Should be ≥ 24
docker compose --version  # Should be ≥ 2.0
```

---

## Service Access Quick Reference

| Service | URL | Credentials |
|---------|-----|-------------|
| **API + Frontend** | `http://localhost:8080` | — |
| **Neo4j Browser** | `http://localhost:7474` | `neo4j` / `password` |
| **Langfuse** | `http://localhost:3100` | See setup |
| **MinIO Console** | `http://localhost:9090` | `minioadmin` / `minioadmin` |

---

## Script Compatibility

All scripts are platform-specific:

- **`.bat` files** → Windows PowerShell / CMD
- **`.sh` files** → Bash (Linux, macOS, Windows WSL2)

Pick the appropriate format for your OS.

---

## Tips & Tricks

- **Real-time logs:** Use `docker compose logs -f <service>` to watch logs
- **Health check:** `docker compose ps` shows container health status
- **Rebuild images:** `docker compose build` (if you modify Dockerfile)
- **Interactive shell:** `docker compose exec <service> /bin/bash`
- **Resource usage:** `docker stats` to monitor CPU/memory

---

## Need Help?

Check for common issues in the main **README.md** or run:
```bash
docker compose ps          # Check status of all services
docker compose logs -f     # Stream all logs
```
