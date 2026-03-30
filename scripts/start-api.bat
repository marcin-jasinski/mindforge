@echo off
REM ============================================================================
REM MindForge API Only Startup — Windows
REM ============================================================================
REM Starts just the FastAPI backend + Angular frontend (+ Neo4j for graph RAG)
REM
REM Usage: start-api.bat
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo [==========================================================================]
echo MindForge API Startup
echo.
echo Services:
echo   - API (FastAPI) + Frontend    http://localhost:8080
echo   - Neo4j graph database        http://localhost:7474
echo.
echo Note: Langfuse observability is NOT started (set ENABLE_TRACING=false)
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

REM Start Docker Compose with API and graph profiles
docker compose --profile gui --profile graph up -d

if !errorlevel! neq 0 (
    echo.
    echo WARNING: One or more containers may not have started cleanly.
    echo          Run 'docker compose logs' to investigate.
)

echo.
echo [OK] Services launched.
echo.
echo Accessing services:
echo   - API + Frontend:  http://localhost:8080
echo   - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)

endlocal
