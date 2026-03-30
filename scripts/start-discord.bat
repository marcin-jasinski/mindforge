@echo off
REM ============================================================================
REM MindForge Discord Bot Startup — Windows
REM ============================================================================
REM Starts the Discord bot (requires Neo4j and other services)
REM
REM Prerequisites:
REM   - DISCORD_TOKEN in .env file
REM   - Discord application created and configured
REM
REM Usage: start-discord.bat
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo [==========================================================================]
echo MindForge Discord Bot Startup
echo.
echo Starts the Discord bot integration
echo.
echo Prerequisites:
echo   - DISCORD_TOKEN configured in .env
echo   - Discord application created and bot invited to server
echo   - Neo4j database with concept graph
echo   - ENABLE_TRACING=false or Langfuse running
echo.
echo Starting services: Neo4j + Langfuse (optional)
echo [==========================================================================]

REM Check if .env file exists
if not exist .env (
    echo ERROR: .env file not found!
    echo Please copy env.example to .env and configure it.
    echo.
    echo Make sure DISCORD_TOKEN is set in .env
    echo.
    pause
    exit /b 1
)

REM Start infrastructure services
echo Starting Neo4j and Langfuse...
docker compose --profile observability --profile graph up -d

timeout /t 3 /nobreak

echo.
echo Starting Discord bot...
docker compose --profile discord --profile graph up discord-bot

endlocal
