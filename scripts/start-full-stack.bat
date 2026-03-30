@echo off
REM ============================================================================
REM MindForge Full Stack Startup — Windows
REM ============================================================================
REM Starts the complete stack:
REM   - FastAPI backend + Angular frontend (port 8080)
REM   - Neo4j graph database (ports 7474, 7687)
REM   - Langfuse observability (port 3100)
REM   - All supporting infrastructure (Postgres, ClickHouse, Redis, MinIO)
REM
REM Usage: start-full-stack.bat
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo [==========================================================================]
echo MindForge Full Stack Startup
echo.
echo Services:
echo   - API (FastAPI)              http://localhost:8080
echo   - Neo4j Browser              http://localhost:7474
echo   - Langfuse Observability     http://localhost:3100
echo   - MinIO Console              http://localhost:9090
echo.
echo Waiting for services to be healthy... (may take 30-60 seconds)
echo [==========================================================================]

REM Check if .env file exists
if not exist .env (
    echo ERROR: .env file not found!
    echo Please copy env.example to .env and configure it.
    echo.
    pause
    exit /b 1
)

REM Check Docker Compose version
for /f "tokens=*" %%i in ('docker compose --version 2^>nul') do set COMPOSE_VERSION=%%i
if "%COMPOSE_VERSION%"==" " (
    echo ERROR: Docker Compose not found
    echo Install Docker Desktop from: https://www.docker.com/products/docker-desktop
    echo.
    pause
    exit /b 1
)

REM Start Docker Compose with all profiles required for the full stack
docker compose --profile gui --profile observability --profile graph up -d

if !errorlevel! neq 0 (
    echo.
    echo WARNING: One or more containers may not have started cleanly.
    echo          Run 'docker compose logs' to investigate.
)

echo.
echo [OK] Services launched. Some may still be initialising — check health with:
echo      docker ps
echo.
echo Accessing services:
echo   - API + Frontend:  http://localhost:8080
echo   - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)
echo   - Langfuse:        http://localhost:3100
echo   - MinIO Console:   http://localhost:9090 (user: minioadmin, pwd: minioadmin)

endlocal
