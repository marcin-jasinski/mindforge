@echo off
REM ============================================================================
REM MindForge Stop All Containers — Windows
REM ============================================================================
REM Stops all running MindForge Docker containers
REM
REM Usage: stop.bat [--clean]
REM        stop.bat         # Stop containers (keep volumes)
REM        stop.bat --clean # Stop and remove containers (keep volumes)
REM ============================================================================

setlocal enabledelayedexpansion

set CLEAN=%1

echo.
echo [==========================================================================]
if "%CLEAN%"=="--clean" (
    echo Stopping and Removing Containers
) else (
    echo Stopping All Containers
)
echo [==========================================================================]

if "%CLEAN%"=="--clean" (
    echo Removing containers...
    docker compose down --remove-orphans
) else (
    echo Stopping containers...
    docker compose stop
)

if !errorlevel! equ 0 (
    echo.
    echo [OK] Done
    if "%CLEAN%"=="--clean" (
        echo.
        echo To fully reset, also run:
        echo   docker compose down -v    # Remove volumes as well
    )
) else (
    echo ERROR: Failed to stop containers
    pause
    exit /b 1
)

endlocal
