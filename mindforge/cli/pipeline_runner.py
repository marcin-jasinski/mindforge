"""
Pipeline worker — CLI entry point for the document processing background process.

Polls ``pipeline_tasks`` using ``SELECT … FOR UPDATE SKIP LOCKED`` so that
multiple worker processes can run safely in parallel without stepping on each
other.

Lifecycle:
  1. On startup: recover stale tasks (status='running', claimed_at too old).
  2. Loop: claim pending task → execute → mark done/failed.
  3. On SIGTERM: stop accepting new tasks and drain in-flight work.

Composition root: ``main()`` wires all dependencies from ``AppSettings`` and
creates the single ``AsyncEngine``.  No module-level singletons anywhere.
"""

from __future__ import annotations

import asyncio
import logging
import os
import re
import signal
import socket
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker

from mindforge.agents import AgentRegistry
from mindforge.application.orchestration import OrchestrationGraph
from mindforge.application.pipeline import PipelineOrchestrator, StepExecutionError
from mindforge.domain.agents import AgentContext, ProcessingSettings
from mindforge.domain.models import DocumentArtifact
from mindforge.domain.ports import AIGateway, ArtifactRepository
from mindforge.infrastructure.ai.gateway import LiteLLMGateway
from mindforge.infrastructure.config import AppSettings
from mindforge.infrastructure.db import create_async_engine
from mindforge.infrastructure.events import OutboxEventPublisher
from mindforge.infrastructure.persistence.artifact_repo import (
    PostgresArtifactRepository,
)
from mindforge.infrastructure.persistence.document_repo import (
    PostgresDocumentRepository,
)
from mindforge.infrastructure.persistence.interaction_repo import (
    PostgresInteractionStore as PostgresInteractionRepository,
)
from mindforge.infrastructure.persistence.models import (
    DocumentModel,
    PipelineTaskModel,
)

logger = logging.getLogger(__name__)

# Maximum number of times a task can be reclaimed before being permanently failed
_MAX_RECLAIM_ATTEMPTS = 3


# ---------------------------------------------------------------------------
# PipelineWorker
# ---------------------------------------------------------------------------


class PipelineWorker:
    """Long-running worker that consumes ``pipeline_tasks`` rows.

    All dependencies are injected; the worker itself is I/O-free at class
    definition time.

    Args:
        worker_id:      Unique string identifying this worker process
                        (used for the ``worker_id`` column in ``pipeline_tasks``).
        engine:         SQLAlchemy :class:`~sqlalchemy.ext.asyncio.AsyncEngine`.
        registry:       :class:`~mindforge.agents.AgentRegistry` with all
                        registered agent implementations.
        graph:          :class:`~mindforge.application.orchestration.OrchestrationGraph`
                        defining the pipeline DAG.
        gateway:        :class:`~mindforge.domain.ports.AIGateway` passed into
                        every :class:`~mindforge.domain.agents.AgentContext`.
        settings:       :class:`~mindforge.domain.agents.ProcessingSettings`
                        derived from ``AppSettings``.
        retrieval:      Retrieval port implementation (stub until Phase 11).
        max_concurrent: Maximum number of tasks to execute in parallel.
        poll_interval:  Seconds between poll cycles when the queue is empty.
        stale_threshold_minutes: Tasks whose ``claimed_at`` is older than this
                        are considered stale and reclaimed on startup.
    """

    def __init__(
        self,
        *,
        worker_id: str,
        engine: AsyncEngine,
        registry: AgentRegistry,
        graph: OrchestrationGraph,
        gateway: AIGateway,
        settings: ProcessingSettings,
        retrieval: Any,
        max_concurrent: int = 2,
        poll_interval: float = 2.0,
        stale_threshold_minutes: int = 30,
    ) -> None:
        self._worker_id = worker_id
        self._engine = engine
        self._registry = registry
        self._graph = graph
        self._gateway = gateway
        self._settings = settings
        self._retrieval = retrieval
        self._max_concurrent = max_concurrent
        self._poll_interval = poll_interval
        self._stale_threshold = timedelta(minutes=stale_threshold_minutes)

        self._session_factory: async_sessionmaker[AsyncSession] = async_sessionmaker(
            engine, expire_on_commit=False
        )
        self._running = False
        self._semaphore = asyncio.Semaphore(max_concurrent)
        self._active_tasks: set[asyncio.Task[None]] = set()

    # ------------------------------------------------------------------
    # Public interface
    # ------------------------------------------------------------------

    async def run_forever(self) -> None:
        """Start the worker loop.

        Runs until :meth:`shutdown` is called or the process receives
        ``SIGTERM``/``SIGINT``.
        """
        self._running = True
        logger.info(
            "PipelineWorker %r starting (max_concurrent=%d)",
            self._worker_id,
            self._max_concurrent,
        )

        await self._recover_stale_tasks()

        while self._running:
            # Respect the concurrency cap without busy-waiting
            if len(self._active_tasks) >= self._max_concurrent:
                await asyncio.sleep(self._poll_interval)
                continue

            task_row = await self._claim_task()
            if task_row is None:
                # Nothing pending — wait before polling again
                await asyncio.sleep(self._poll_interval)
                continue

            async def _run(t: PipelineTaskModel) -> None:
                async with self._semaphore:
                    await self._execute_task(t)

            at = asyncio.create_task(_run(task_row))
            self._active_tasks.add(at)
            at.add_done_callback(self._active_tasks.discard)

        # Drain in-flight tasks
        if self._active_tasks:
            logger.info(
                "Draining %d in-flight tasks…",
                len(self._active_tasks),
            )
            await asyncio.gather(*self._active_tasks, return_exceptions=True)

        logger.info("PipelineWorker %r stopped.", self._worker_id)

    async def shutdown(self, timeout_seconds: float = 30.0) -> None:
        """Signal the worker to stop accepting new tasks.

        Waits up to *timeout_seconds* for in-flight tasks to complete.
        """
        logger.info("Shutdown requested for worker %r", self._worker_id)
        self._running = False
        if self._active_tasks:
            try:
                await asyncio.wait_for(
                    asyncio.gather(*self._active_tasks, return_exceptions=True),
                    timeout=timeout_seconds,
                )
            except asyncio.TimeoutError:
                logger.warning(
                    "Graceful drain timed out after %.0fs; %d tasks still running.",
                    timeout_seconds,
                    len(self._active_tasks),
                )

    # ------------------------------------------------------------------
    # Task claim
    # ------------------------------------------------------------------

    async def _claim_task(self) -> PipelineTaskModel | None:
        """Atomically claim one pending task using ``SELECT … FOR UPDATE SKIP LOCKED``.

        Returns ``None`` if no pending task is available.
        """
        async with self._session_factory() as session, session.begin():
            result = await session.execute(
                select(PipelineTaskModel)
                .where(PipelineTaskModel.status == "pending")
                .order_by(PipelineTaskModel.submitted_at)
                .limit(1)
                .with_for_update(skip_locked=True)
            )
            row = result.scalar_one_or_none()
            if row is None:
                return None

            now = datetime.now(timezone.utc)
            row.status = "running"
            row.worker_id = self._worker_id
            row.claimed_at = now
            row.started_at = now
            await session.flush()

            # Detach from session so we can use it from a fresh session later
            session.expunge(row)
            return row

    # ------------------------------------------------------------------
    # Task execution
    # ------------------------------------------------------------------

    async def _execute_task(self, task: PipelineTaskModel) -> None:
        """Load the document and artifact, build the context, and run the
        orchestrator.  Marks the task as ``done`` or ``failed`` when finished.

        A fresh set of repositories and a fresh :class:`PipelineOrchestrator`
        are created for each task so that concurrent executions never share
        session state.  All database writes for a single task (artifact
        checkpoints, outbox events, document status, task status) are
        committed within the same session transaction.
        """
        task_id: uuid.UUID = task.task_id
        document_id: uuid.UUID = task.document_id
        logger.info("Executing task %s for document %s", task_id, document_id)

        async with self._session_factory() as session, session.begin():
            # Build per-task repos and orchestrator — no shared session state.
            artifact_repo = PostgresArtifactRepository(session)
            document_repo = PostgresDocumentRepository(session)
            interaction_repo = PostgresInteractionRepository(session)
            publisher = OutboxEventPublisher(session)

            orchestrator = PipelineOrchestrator(
                registry=self._registry,
                graph=self._graph,
                artifact_repo=artifact_repo,
                event_publisher=publisher,
                interaction_store=interaction_repo,
                document_repo=document_repo,
            )

            doc_row = await session.get(DocumentModel, document_id)
            if doc_row is None:
                logger.error(
                    "Task %s: document %s not found — marking failed",
                    task_id,
                    document_id,
                )
                await self._mark_failed(task_id, "Document not found", session=session)
                return

            artifact = await self._load_or_create_artifact(artifact_repo, doc_row)

            context = AgentContext(
                document_id=document_id,
                knowledge_base_id=doc_row.knowledge_base_id,
                artifact=artifact,
                gateway=self._gateway,
                retrieval=self._retrieval,
                settings=self._settings,
            )

            try:
                await orchestrator.run(
                    document_id=document_id,
                    artifact=artifact,
                    context=context,
                )
            except StepExecutionError as exc:
                logger.error(
                    "Task %s failed at step %r: %s", task_id, exc.step, exc.reason
                )
                await self._mark_failed(task_id, exc.reason, session=session)
                return
            except Exception as exc:
                logger.exception("Task %s raised unexpected error", task_id)
                await self._mark_failed(task_id, str(exc), session=session)
                return

            await self._mark_done(task_id, session=session)
            logger.info("Task %s completed successfully", task_id)

    async def _load_or_create_artifact(
        self, artifact_repo: ArtifactRepository, doc_row: DocumentModel
    ) -> DocumentArtifact:
        """Return the latest stored artifact for this document, or create a fresh one."""
        existing = await artifact_repo.load_latest(doc_row.document_id)
        if existing is not None:
            return existing
        return DocumentArtifact(
            document_id=doc_row.document_id,
            knowledge_base_id=doc_row.knowledge_base_id,
            lesson_id=doc_row.lesson_id,
            version=1,
            created_at=datetime.now(timezone.utc),
        )

    # ------------------------------------------------------------------
    # Status helpers
    # ------------------------------------------------------------------

    async def _mark_done(self, task_id: uuid.UUID, *, session: AsyncSession) -> None:
        await session.execute(
            update(PipelineTaskModel)
            .where(PipelineTaskModel.task_id == task_id)
            .values(status="done", completed_at=datetime.now(timezone.utc))
        )

    async def _mark_failed(
        self,
        task_id: uuid.UUID,
        reason: str,
        *,
        session: AsyncSession,
    ) -> None:
        await session.execute(
            update(PipelineTaskModel)
            .where(PipelineTaskModel.task_id == task_id)
            .values(
                status="failed",
                error=reason[:2000],
                completed_at=datetime.now(timezone.utc),
            )
        )

    # ------------------------------------------------------------------
    # Stale task recovery
    # ------------------------------------------------------------------

    async def _recover_stale_tasks(self) -> None:
        """On startup, reclaim tasks that a previous worker died holding.

        Tasks with ``status='running'`` and ``claimed_at`` older than
        ``stale_threshold`` are reset to ``pending`` (if reclaim_count < 3)
        or permanently failed.
        """
        cutoff = datetime.now(timezone.utc) - self._stale_threshold

        async with self._session_factory() as session, session.begin():
            result = await session.execute(
                select(PipelineTaskModel)
                .where(
                    PipelineTaskModel.status == "running",
                    PipelineTaskModel.claimed_at < cutoff,
                )
                .with_for_update(skip_locked=True)
            )
            stale = result.scalars().all()

            for row in stale:
                reclaim_count = _parse_reclaim_count(row.error)
                if reclaim_count >= _MAX_RECLAIM_ATTEMPTS:
                    logger.warning(
                        "Stale task %s exceeded max reclaim attempts — failing permanently",
                        row.task_id,
                    )
                    row.status = "failed"
                    row.error = (
                        f"[reclaim:{reclaim_count}] Exceeded max reclaim attempts"
                    )
                    row.completed_at = datetime.now(timezone.utc)
                else:
                    logger.info(
                        "Reclaiming stale task %s (reclaim_count=%d)",
                        row.task_id,
                        reclaim_count + 1,
                    )
                    row.status = "pending"
                    row.worker_id = None
                    row.claimed_at = None
                    row.started_at = None
                    row.error = (
                        f"[reclaim:{reclaim_count + 1}] Reclaimed after stale detection"
                    )

        if stale:
            logger.info("Stale task recovery: processed %d task(s)", len(stale))


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _parse_reclaim_count(error: str | None) -> int:
    """Extract the ``[reclaim:N]`` counter embedded in the error field."""
    if not error:
        return 0
    match = re.search(r"\[reclaim:(\d+)\]", error)
    return int(match.group(1)) if match else 0


def _make_worker_id() -> str:
    """Return a unique worker identifier combining hostname and PID."""
    return f"{socket.gethostname()}-{os.getpid()}"


# ---------------------------------------------------------------------------
# Composition root
# ---------------------------------------------------------------------------


def main() -> None:
    """Entry point for the ``mindforge-pipeline`` CLI command.

    Wires all dependencies from environment / ``.env``, then runs the
    worker loop until ``SIGTERM`` or ``SIGINT``.
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    async def _run() -> None:
        settings = AppSettings()
        engine = create_async_engine(settings.database_url)

        gateway = LiteLLMGateway(
            default_model=settings.model_large,
            model_map=settings.model_map,
            fallback_models=[settings.model_fallback],
            timeout_seconds=300,
            max_retries=3,
        )

        processing_settings = ProcessingSettings(
            chunk_max_tokens=settings.chunk_max_tokens,
            chunk_min_tokens=settings.chunk_min_tokens,
            chunk_overlap_tokens=settings.chunk_overlap_tokens,
            enable_graph=settings.enable_graph,
            enable_image_analysis=settings.enable_image_analysis,
            model_tier_map=settings.model_map,
        )

        registry = AgentRegistry()
        # Agents registered in Phase 6.

        graph = OrchestrationGraph.default()

        class _StubRetrieval:
            async def retrieve(self, *a: object, **kw: object) -> list:
                return []

            async def retrieve_concept_neighborhood(
                self, *a: object, **kw: object
            ) -> None:
                return None

            async def find_weak_concepts(self, *a: object, **kw: object) -> list:
                return []

            async def get_concepts(self, *a: object, **kw: object) -> list:
                return []

            async def get_lesson_concepts(self, *a: object, **kw: object) -> list:
                return []

        # PipelineWorker builds a fresh per-task orchestrator (with its own
        # session) on each _execute_task call, so no long-lived shared session
        # is required here.
        worker = PipelineWorker(
            worker_id=_make_worker_id(),
            engine=engine,
            registry=registry,
            graph=graph,
            gateway=gateway,
            settings=processing_settings,
            retrieval=_StubRetrieval(),
            max_concurrent=settings.max_concurrent_pipelines,
            stale_threshold_minutes=settings.pipeline_task_stale_threshold_minutes,
        )

        loop = asyncio.get_running_loop()

        def _on_signal() -> None:
            logger.info("Shutdown signal received")
            asyncio.create_task(worker.shutdown())

        for sig in (signal.SIGTERM, signal.SIGINT):
            try:
                loop.add_signal_handler(sig, _on_signal)
            except NotImplementedError:
                # Windows does not support add_signal_handler for all signals
                pass

        await worker.run_forever()
        await engine.dispose()

    asyncio.run(_run())
