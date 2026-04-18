"""
FastAPI dependency providers.

All providers read from ``request.app.state`` — zero module-level globals.
Separation of concerns: routers import providers; they never access
``app.state`` directly.
"""

from __future__ import annotations

import logging
from typing import Annotated
from uuid import UUID

try:
    import jwt as _jwt
except ImportError:  # pragma: no cover
    _jwt = None  # type: ignore[assignment]

from fastapi import Cookie, Depends, HTTPException, Request, status
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from mindforge.api.auth import JWTService
from mindforge.domain.models import User
from mindforge.infrastructure.config import AppSettings

log = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Settings
# ---------------------------------------------------------------------------


def get_settings(request: Request) -> AppSettings:
    return request.app.state.settings


# ---------------------------------------------------------------------------
# Database session — one per request, auto-closed
# ---------------------------------------------------------------------------


async def get_db_session(request: Request) -> AsyncSession:  # type: ignore[return]
    session_factory: async_sessionmaker[AsyncSession] = request.app.state.session_factory
    async with session_factory() as session:
        yield session


# ---------------------------------------------------------------------------
# Infrastructure adapters pulled from app.state
# ---------------------------------------------------------------------------


def get_gateway(request: Request):
    return request.app.state.gateway


def get_artifact_repo(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.persistence.artifact_repo import (
        PostgresArtifactRepository,
    )

    return PostgresArtifactRepository(session)


def get_doc_repo(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.persistence.document_repo import (
        PostgresDocumentRepository,
    )

    return PostgresDocumentRepository(session)


def get_kb_repo(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.persistence.kb_repo import (
        PostgresKnowledgeBaseRepository,
    )

    return PostgresKnowledgeBaseRepository(session)


def get_identity_repo(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.persistence.identity_repo import (
        PostgresIdentityRepository,
    )

    return PostgresIdentityRepository(session)


def get_interaction_store(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.persistence.interaction_repo import (
        PostgresInteractionStore,
    )

    return PostgresInteractionStore(session)


def get_study_progress(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.persistence.study_progress_repo import (
        PostgresStudyProgressRepository,
    )

    return PostgresStudyProgressRepository(session)


def get_pipeline_task_repo(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.persistence.pipeline_task_repo import (
        PostgresPipelineTaskRepository,
    )

    return PostgresPipelineTaskRepository(session)


def get_event_publisher(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    from mindforge.infrastructure.events.outbox_publisher import OutboxEventPublisher

    return OutboxEventPublisher(session)


def get_retrieval(request: Request):
    return request.app.state.retrieval


def get_quiz_sessions(request: Request):
    return request.app.state.quiz_session_store


def get_ingestion(
    request: Request,
    session: Annotated[AsyncSession, Depends(get_db_session)],
):
    """Build an IngestionService scoped to the current request's session."""
    from mindforge.application.ingestion import IngestionService
    from mindforge.infrastructure.events.outbox_publisher import OutboxEventPublisher
    from mindforge.infrastructure.parsing.registry import ParserRegistry
    from mindforge.infrastructure.persistence.artifact_repo import (
        PostgresArtifactRepository,
    )
    from mindforge.infrastructure.persistence.document_repo import (
        PostgresDocumentRepository,
    )
    from mindforge.infrastructure.persistence.pipeline_task_repo import (
        PostgresPipelineTaskRepository,
    )
    from mindforge.infrastructure.security.upload_sanitizer import UploadSanitizer

    settings: AppSettings = request.app.state.settings
    return IngestionService(
        document_repo=PostgresDocumentRepository(session),
        sanitizer=UploadSanitizer(
            max_size_bytes=settings.max_document_size_mb * 1024 * 1024
        ),
        parser_registry=request.app.state.parser_registry,
        task_store=PostgresPipelineTaskRepository(session),
        event_publisher=OutboxEventPublisher(session),
    )


# ---------------------------------------------------------------------------
# JWT service
# ---------------------------------------------------------------------------


def get_jwt_service(request: Request) -> JWTService:
    return request.app.state.jwt_service


# ---------------------------------------------------------------------------
# Current user — resolved from HttpOnly cookie or Authorization header
# ---------------------------------------------------------------------------


async def get_current_user(
    request: Request,
    access_token: Annotated[str | None, Cookie(alias="access_token")] = None,
) -> User:
    """Extract and validate the JWT access token; return the associated User.

    Accepts the token from:
    1. ``access_token`` HttpOnly cookie (browser clients)
    2. ``Authorization: Bearer <token>`` header (CLI / bot clients)
    """
    if _jwt is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="JWT support not available.",
        )

    token: str | None = access_token
    if token is None:
        auth_header = request.headers.get("Authorization", "")
        if auth_header.startswith("Bearer "):
            token = auth_header[len("Bearer "):]

    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Nie jesteś zalogowany.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    jwt_service: JWTService = request.app.state.jwt_service
    try:
        payload = jwt_service.decode_access(token)
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token jest nieprawidłowy lub wygasł.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    user_id_str: str = payload.get("sub", "")
    try:
        user_id = UUID(user_id_str)
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token zawiera nieprawidłowy identyfikator użytkownika.",
        )

    # Load user from DB
    session_factory: async_sessionmaker[AsyncSession] = request.app.state.session_factory
    async with session_factory() as session:
        from mindforge.infrastructure.persistence.identity_repo import (
        PostgresIdentityRepository,
        from mindforge.infrastructure.persistence.models import UserModel
        from sqlalchemy import select

        result = await session.execute(
            select(UserModel).where(UserModel.user_id == user_id)
        )
        user_row = result.scalar_one_or_none()

    if user_row is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Użytkownik nie istnieje.",
        )

    from mindforge.domain.models import User as DomainUser

    return DomainUser(
        user_id=user_row.user_id,
        display_name=user_row.display_name,
        email=user_row.email,
        password_hash=user_row.password_hash,
        avatar_url=user_row.avatar_url,
        created_at=user_row.created_at,
        last_login_at=user_row.last_login_at,
    )


async def get_optional_user(
    request: Request,
    access_token: Annotated[str | None, Cookie(alias="access_token")] = None,
) -> User | None:
    """Like ``get_current_user`` but returns ``None`` instead of raising 401."""
    try:
        return await get_current_user(request, access_token)
    except HTTPException:
        return None
