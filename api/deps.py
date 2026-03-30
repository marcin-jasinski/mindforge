"""
Dependency injection for FastAPI — Neo4j driver, LLM client, config.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from fastapi import Depends, Request

log = logging.getLogger(__name__)


def get_config(request: Request) -> Any:
    """Get the processor Config from app state (legacy — prefer get_settings)."""
    return request.app.state.config


def get_settings(request: Request) -> Any:
    """Get the AppSettings (non-secret config) from app state."""
    return request.app.state.settings


def get_neo4j_driver(request: Request) -> Any:
    """Get the Neo4j driver from app state."""
    driver = request.app.state.neo4j_driver
    if driver is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=503, detail="Neo4j is not available")
    return driver


def get_llm_client(request: Request) -> Any:
    """Get the synchronous LLMClient (for background pipeline tasks only).

    Route handlers must use :func:`get_async_llm_client` to avoid blocking
    the event loop.
    """
    return request.app.state.config.llm


def get_async_llm_client(request: Request) -> Any:
    """Get the AsyncLLMClient for use in FastAPI route handlers."""
    return request.app.state.async_llm


def get_base_dir(request: Request) -> Path:
    """Get the project base directory."""
    return request.app.state.settings.base_dir


def get_sr_state_path(request: Request) -> Path:
    """Get the path to the SR state file."""
    return request.app.state.settings.base_dir / "state" / "sr_state.json"
