@echo off
REM ============================================================================
REM MindForge Development Mode Startup — Windows
REM ============================================================================
REM Starts the Python development environment with:
REM   - Virtual environment activation
REM   - Markdown summarizer in watch mode
REM   - Neo4j database (optional)
REM
REM Prerequisites:
REM   - Python 3.9+ with venv created: python -m venv venv
REM   - Dependencies installed: pip install -r requirements.txt
REM
REM Usage: start-dev.bat [--with-db]
REM        start-dev.bat              # Watch mode only
REM        start-dev.bat --with-db    # Watch mode + Neo4j
REM ============================================================================

setlocal enabledelayedexpansion

REM Detect virtual environment location
set VENV_DIR=venv
if exist ".venv" set VENV_DIR=.venv
if exist "venv" set VENV_DIR=venv

REM Check if venv exists
if not exist "!VENV_DIR!" (
    echo.
    echo ERROR: Virtual environment not found at "!VENV_DIR!"
    echo.
    echo Please create it with:
    echo   python -m venv venv
    echo   venv\Scripts\activate
    echo   pip install -r requirements.txt
    echo.
    pause
    exit /b 1
)

echo.
echo [==========================================================================]
echo MindForge Development Mode
echo.
echo Running in local Python environment (watch mode)
echo Watching: new/ folder for markdown files
echo Output: summarized/, flashcards/, diagrams/, knowledge/
echo.
if "%1"=="--with-db" (
    echo Extras: Neo4j database running on localhost:7687
    echo         (Browser: http://localhost:7474)
) else (
    echo Tip: Use 'start-dev.bat --with-db' to also start Neo4j
)
echo.
echo Press Ctrl+C to stop          (logs will show all pipeline activity)
echo [==========================================================================]

REM Check if .env file exists
if not exist .env (
    echo ERROR: .env file not found!
    echo Please copy env.example to .env and configure it.
    echo.
    pause
    exit /b 1
)

REM Start Neo4j if requested
if "%1"=="--with-db" (
    echo Starting Neo4j database...
    docker compose --profile graph up -d
    timeout /t 2 /nobreak
)

REM Activate venv and run the summarizer
cd /d "%CD%"
call "!VENV_DIR!\Scripts\activate.bat"

echo.
echo [OK] Virtual environment activated
echo [OK] Starting markdown summarizer in watch mode...

python mindforge.py --watch

endlocal
