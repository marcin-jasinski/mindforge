@echo off
REM ============================================================================
REM MindForge Services Infrastructure Only — Windows
REM ============================================================================
REM Starts supporting services (Docker infrastructure) without the application:
REM   - Neo4j graph database (ports 7474, 7687)
REM   - Langfuse observability (port 3100)
REM   - Backing stores: Postgres, ClickHouse, Redis, MinIO
REM
REM Use this to run the app locally while keeping databases in Docker.
REM
REM Usage: start-services.bat [full|slim]
REM        start-services.bat       # Both Neo4j and Langfuse
REM        start-services.bat full  # Neo4j + full Langfuse stack
REM        start-services.bat slim  # Neo4j only
REM ============================================================================

setlocal enabledelayedexpansion

set MODE=%1
if "%MODE%"=="" set MODE=full

echo.
echo [==========================================================================]
echo MindForge Services Infrastructure

if "%MODE%"=="full" (
    echo.
    echo Services:
    echo   - Neo4j graph database        http://localhost:7474
    echo   - Langfuse Observability     http://localhost:3100
    echo   - MinIO Console              http://localhost:9090
    echo.
    echo Run your app locally with Python while this manages infrastructure
)

if "%MODE%"=="slim" (
    echo.
    echo Services:
    echo   - Neo4j graph database        http://localhost:7474
    echo.
    echo Minimal setup for local development
)

echo [==========================================================================]

REM Start services based on mode
if "%MODE%"=="full" (
    docker compose --profile observability --profile graph up -d
) else if "%MODE%"=="slim" (
    docker compose --profile graph up -d
) else (
    echo ERROR: Invalid mode "%MODE%". Use 'full' or 'slim'
    pause
    exit /b 1
)

if !errorlevel! neq 0 (
    echo.
    echo WARNING: One or more containers may not have started cleanly.
    echo          Run 'docker compose logs' to investigate.
)

echo.
echo [OK] Services launched.
if "%MODE%"=="full" (
    echo.
    echo Access points:
    echo   - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)
    echo   - Langfuse:        http://localhost:3100
    echo   - MinIO Console:   http://localhost:9090 (user: minioadmin, pwd: minioadmin)
    echo.
    echo Run your application locally:
    echo   start-dev.bat --with-db
) else if "%MODE%"=="slim" (
    echo.
    echo Access points:
    echo   - Neo4j Browser:   http://localhost:7474 (user: neo4j, pwd: password)
    echo.
    echo Run your application locally:
    echo   start-dev.bat --with-db
)

endlocal
