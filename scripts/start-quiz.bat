@echo off
REM ============================================================================
REM MindForge Quiz Agent Startup — Windows
REM ============================================================================
REM Starts the interactive quiz agent (requires Neo4j and other services)
REM
REM Usage: start-quiz.bat
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo [==========================================================================]
echo MindForge Quiz Agent Startup
echo.
echo Starts the interactive assessment runner powered by graph-RAG
echo.
echo Prerequisites:
echo   - Neo4j database with concept graph populated
echo   - ENABLE_TRACING=false or Langfuse running
echo.
echo Starting services: Neo4j + Langfuse (optional)
echo [==========================================================================]

REM Check if .env file exists
if not exist .env (
    echo ERROR: .env file not found!
    echo Please copy env.example to .env and configure it.
    echo.
    pause
    exit /b 1
)

REM Start services
echo Starting Neo4j and Langfuse...
docker compose --profile observability --profile graph up -d

timeout /t 3 /nobreak

REM Start quiz agent
docker compose --profile quiz run --rm quiz-agent

endlocal
