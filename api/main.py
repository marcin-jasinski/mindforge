"""
MindForge API — FastAPI application entry point.

Serves the REST API and (when built) the Angular SPA as static files.

Usage:
  uvicorn api.main:app --host 0.0.0.0 --port 8080 --reload
"""
from __future__ import annotations

import logging
import sys
from contextlib import asynccontextmanager
from pathlib import Path
from typing import AsyncGenerator

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

# Ensure project root is on sys.path
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from api.schemas import HealthResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)-7s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("mindforge-api")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Initialize shared resources on startup, clean up on shutdown."""
    from processor.settings import load_settings
    from processor.llm_client import load_credentials, LLMClient, AsyncLLMClient, Config

    settings = load_settings(base_dir=ROOT)
    creds = load_credentials(base_dir=ROOT)
    app.state.settings = settings
    log.info("Settings loaded (base_dir=%s)", ROOT)

    # Initialize tracing explicitly — single call, no load_config side-effect.
    from processor import tracing
    tracing.init_tracing(settings, creds)

    # Build sync LLM client for background pipeline tasks
    llm = LLMClient(
        base_url=creds.base_url,
        api_key=creds.api_key,
        default_headers=creds.extra_headers,
    )

    # Build Config compatibility shim used by pipeline.run() and routers
    # that have not yet been migrated to AppSettings.
    config = Config(
        llm=llm,
        model_small=settings.model_small,
        model_large=settings.model_large,
        model_vision=settings.model_vision,
        base_dir=settings.base_dir,
        nowe_dir=settings.new_dir,
        podsumowane_dir=settings.summaries_dir,
        archiwum_dir=settings.archive_dir,
        flashcards_dir=settings.flashcards_dir,
        quizzes_dir=settings.quizzes_dir,
        diagrams_dir=settings.diagrams_dir,
        knowledge_dir=settings.knowledge_dir,
        state_file=settings.state_file,
        enable_image_analysis=settings.enable_image_analysis,
        enable_flashcards=settings.enable_flashcards,
        enable_quizzes=settings.enable_quizzes,
        enable_diagrams=settings.enable_diagrams,
        enable_knowledge_index=settings.enable_knowledge_index,
        enable_validation=settings.enable_validation,
        enable_tracing=settings.enable_tracing,
        enable_graph_rag=settings.enable_graph_rag,
        enable_embeddings=settings.enable_embeddings,
        neo4j_uri=settings.neo4j_uri,
        neo4j_username=settings.neo4j_username,
        neo4j_password=creds.neo4j_password,
        model_embedding=settings.model_embedding,
    )
    app.state.config = config

    # Build async LLM client for route handlers (non-blocking)
    async_llm = AsyncLLMClient(
        base_url=creds.base_url,
        api_key=creds.api_key,
        default_headers=creds.extra_headers,
    )
    app.state.async_llm = async_llm
    log.info("AsyncLLMClient ready")

    # Connect to Neo4j if graph-RAG is enabled
    driver = None
    if settings.enable_graph_rag:
        from processor.tools.graph_rag import GraphConfig, connect, ensure_indexes

        graph_cfg = GraphConfig(
            uri=settings.neo4j_uri,
            username=settings.neo4j_username,
            password=creds.neo4j_password,
        )
        try:
            driver = connect(graph_cfg)
            ensure_indexes(driver)
            log.info("Neo4j connected")
        except Exception:
            log.warning("Neo4j unavailable — graph endpoints will return 503", exc_info=True)
            driver = None

    app.state.neo4j_driver = driver

    yield  # ── app is running ──

    # Shutdown
    if driver:
        driver.close()
        log.info("Neo4j driver closed")
    tracing.shutdown()


app = FastAPI(
    title="MindForge API",
    version="0.1.0",
    lifespan=lifespan,
)

# CORS for local Angular dev server
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:4200", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Register routers ───────────────────────────────────────────────

from api.auth import router as auth_router
from api.routers.lessons import router as lessons_router
from api.routers.concepts import router as concepts_router
from api.routers.quiz import router as quiz_router
from api.routers.flashcards import router as flashcards_router
from api.routers.search import router as search_router
from api.routers.admin import router as admin_router

app.include_router(auth_router)
app.include_router(lessons_router)
app.include_router(concepts_router)
app.include_router(quiz_router)
app.include_router(flashcards_router)
app.include_router(search_router)
app.include_router(admin_router)


# ── Health check ────────────────────────────────────────────────────


@app.get("/api/health", response_model=HealthResponse)
async def health(request: Request):
    neo4j_status = "connected" if request.app.state.neo4j_driver else "unavailable"
    return HealthResponse(status="ok", neo4j=neo4j_status)


# ── SPA fallback ────────────────────────────────────────────────────
# Serve Angular dist if it exists; fall back to index.html for client-side routing.

_static_dir = ROOT / "frontend" / "dist" / "frontend" / "browser"
if _static_dir.is_dir():
    _assets_dir = _static_dir / "assets"
    if _assets_dir.is_dir():
        app.mount("/assets", StaticFiles(directory=_assets_dir), name="assets")

    @app.get("/{full_path:path}")
    async def spa_fallback(full_path: str):
        """Serve Angular SPA — static files or index.html fallback."""
        file_path = _static_dir / full_path
        if file_path.is_file():
            return FileResponse(file_path)
        index = _static_dir / "index.html"
        if index.is_file():
            return FileResponse(index)
        return JSONResponse({"detail": "Frontend not built"}, status_code=404)
