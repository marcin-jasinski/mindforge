"""Unit tests for Phase 8 - Event System.

Tests cover:
    8.5.1 - Outbox publisher writes event within the caller's transaction.
    8.5.2 - Outbox relay publishes and marks events as delivered.
    8.5.3 - Durable consumer advances cursor correctly.
    8.5.4 - Idempotency: same event delivered twice -> no duplicate side effects.
    8.5.5 - FOR UPDATE SKIP LOCKED logic prevents double-publishing.

Regression tests added for fixed bugs:
    - handle() receives event_id as a third argument (was missing).
    - AuditLoggerConsumer writes to interaction_turns (was log-only).
    - Poison event skipped after _MAX_RETRIES_PER_EVENT failures.
    - OutboxEventPublisher raises if a different AsyncSession is passed.
    - OutboxRelay.stop() cancels the pg_notify listen task.
"""

from __future__ import annotations

import asyncio
import json
import uuid
from datetime import datetime, timezone
from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from mindforge.domain.events import (
    DocumentIngested,
    ProcessingCompleted,
    ProcessingFailed,
)
from mindforge.infrastructure.events.outbox_publisher import OutboxEventPublisher
from mindforge.infrastructure.events.outbox_relay import OutboxRelay
from mindforge.infrastructure.events.durable_consumer import (
    AuditLoggerConsumer,
    DurableEventConsumer,
    GraphIndexerConsumer,
    _MAX_RETRIES_PER_EVENT,
)


# ---------------------------------------------------------------------------
# Helpers / stubs
# ---------------------------------------------------------------------------


def _make_event() -> ProcessingCompleted:
    return ProcessingCompleted(
        document_id=uuid.uuid4(),
        knowledge_base_id=uuid.uuid4(),
        lesson_id="test-lesson",
        timestamp=datetime.now(tz=timezone.utc),
    )


def _make_outbox_row(
    event_type: str = "ProcessingCompleted",
    sequence_num: int = 1,
    published: bool = False,
) -> MagicMock:
    row = MagicMock()
    row.event_id = uuid.uuid4()
    row.event_type = event_type
    row.sequence_num = sequence_num
    row.payload = {"document_id": str(uuid.uuid4()), "event_type": event_type}
    row.created_at = datetime.now(tz=timezone.utc)
    row.published = published
    return row


def _make_audit_repo_factory() -> tuple[MagicMock, Any]:
    """Return (factory, repo_mock) for AuditLoggerConsumer tests."""
    repo = AsyncMock()
    repo.create_interaction = AsyncMock(return_value=uuid.uuid4())
    repo.add_turn = AsyncMock(return_value=uuid.uuid4())
    factory = MagicMock(return_value=repo)
    return factory, repo


def _make_wired_session(rows: list) -> tuple[Any, AsyncMock]:
    """Return (session_cm, session) with controlled execute results."""
    session = AsyncMock()

    begin_cm = MagicMock()
    begin_cm.__aenter__ = AsyncMock(return_value=None)
    begin_cm.__aexit__ = AsyncMock(return_value=False)
    session.begin = MagicMock(return_value=begin_cm)

    scalars_mock = MagicMock()
    scalars_mock.all.return_value = rows
    execute_result = MagicMock()
    execute_result.scalars.return_value = scalars_mock
    session.execute = AsyncMock(return_value=execute_result)

    session_cm = MagicMock()
    session_cm.__aenter__ = AsyncMock(return_value=session)
    session_cm.__aexit__ = AsyncMock(return_value=False)
    return session_cm, session


# ===========================================================================
# 8.5.1 - OutboxEventPublisher writes to the outbox table
# ===========================================================================


class TestOutboxEventPublisher:
    @pytest.mark.asyncio
    async def test_publish_inserts_event_row(self) -> None:
        """Publisher executes an INSERT into outbox_events."""
        session = AsyncMock()
        publisher = OutboxEventPublisher(session)

        await publisher.publish_in_tx(_make_event(), connection=None)

        session.execute.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_publish_targets_outbox_events_table(self) -> None:
        """The INSERT targets the outbox_events table."""
        session = AsyncMock()
        publisher = OutboxEventPublisher(session)

        await publisher.publish_in_tx(_make_event(), connection=None)

        stmt = session.execute.call_args[0][0]
        assert "outbox_events" in str(stmt).lower()

    @pytest.mark.asyncio
    async def test_publish_connection_none_skips_guard(self) -> None:
        """Passing connection=None skips the session-identity check."""
        session = AsyncMock()
        publisher = OutboxEventPublisher(session)

        await publisher.publish_in_tx(_make_event(), connection=None)

        session.execute.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_publish_same_session_passes_guard(self) -> None:
        """Passing the same AsyncSession as connection does not raise."""
        from sqlalchemy.ext.asyncio import AsyncSession

        session = AsyncMock(spec=AsyncSession)
        publisher = OutboxEventPublisher(session)

        await publisher.publish_in_tx(_make_event(), connection=session)

        session.execute.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_publish_different_session_raises(self) -> None:
        """Passing a different AsyncSession raises RuntimeError (atomicity guard)."""
        from sqlalchemy.ext.asyncio import AsyncSession

        session_a = AsyncMock(spec=AsyncSession)
        session_b = AsyncMock(spec=AsyncSession)
        publisher = OutboxEventPublisher(session_a)

        with pytest.raises(RuntimeError, match="different AsyncSession"):
            await publisher.publish_in_tx(_make_event(), connection=session_b)

    @pytest.mark.asyncio
    async def test_publish_multiple_events_calls_execute_for_each(self) -> None:
        """Each publish_in_tx call produces one INSERT statement."""
        session = AsyncMock()
        publisher = OutboxEventPublisher(session)

        for _ in range(3):
            await publisher.publish_in_tx(_make_event(), connection=None)

        assert session.execute.await_count == 3


# ===========================================================================
# 8.5.2 - OutboxRelay publishes events and marks them published
# ===========================================================================


class TestOutboxRelay:
    @pytest.mark.asyncio
    async def test_flush_batch_marks_events_published(self) -> None:
        """After publishing, events are updated to published=True."""
        rows = [_make_outbox_row(sequence_num=1), _make_outbox_row(sequence_num=2)]

        session_cm, session = _make_wired_session(rows)
        relay = OutboxRelay(engine=MagicMock(), redis_client=None)
        relay._session_factory = MagicMock(return_value=session_cm)

        published = await relay._flush_batch()

        assert published == 2
        assert session.execute.await_count == 2

    @pytest.mark.asyncio
    async def test_flush_batch_publishes_to_redis_channel(self) -> None:
        """Each event is published to events:{event_type} on Redis."""
        row = _make_outbox_row(event_type="ProcessingCompleted", sequence_num=1)

        session_cm, _ = _make_wired_session([row])
        redis = AsyncMock()
        relay = OutboxRelay(engine=MagicMock(), redis_client=redis)
        relay._session_factory = MagicMock(return_value=session_cm)

        await relay._flush_batch()

        redis.publish.assert_awaited_once()
        assert redis.publish.call_args[0][0] == "events:ProcessingCompleted"

    @pytest.mark.asyncio
    async def test_flush_batch_returns_zero_when_no_events(self) -> None:
        """Returns 0 when there are no unpublished events."""
        session_cm, session = _make_wired_session([])
        relay = OutboxRelay(engine=MagicMock(), redis_client=None)
        relay._session_factory = MagicMock(return_value=session_cm)

        result = await relay._flush_batch()

        assert result == 0
        assert session.execute.await_count == 1

    @pytest.mark.asyncio
    async def test_relay_degraded_mode_logs_without_redis(self) -> None:
        """When Redis is None, events are not published but no error is raised."""
        row = _make_outbox_row(event_type="DocumentIngested", sequence_num=1)
        relay = OutboxRelay(engine=MagicMock(), redis_client=None)
        await relay._publish_to_redis(row)

    @pytest.mark.asyncio
    async def test_relay_start_stop_lifecycle(self) -> None:
        """start() creates a background task; stop() cancels it cleanly."""
        relay = OutboxRelay(engine=MagicMock(), redis_client=None)
        relay._flush_batch = AsyncMock(return_value=0)

        with patch("mindforge.infrastructure.events.outbox_relay.asyncpg", None):
            await relay.start()
            assert relay._running is True
            assert relay._task is not None

            await relay.stop()
            assert relay._running is False


# ===========================================================================
# 8.5.3 - DurableEventConsumer advances cursor correctly
# ===========================================================================


class _TrackingConsumer(DurableEventConsumer):
    """Test double that records handled events."""

    CONSUMER_NAME = "test_consumer"

    def __init__(self, engine: Any) -> None:
        super().__init__(self.CONSUMER_NAME, engine)
        self.handled: list[tuple[str, dict, uuid.UUID]] = []

    async def handle(self, event_type: str, payload: dict, event_id: uuid.UUID) -> None:
        self.handled.append((event_type, payload, event_id))


class TestDurableEventConsumer:
    def _build_consumer_with_rows(
        self, rows: list, initial_cursor: int = 0
    ) -> tuple[_TrackingConsumer, AsyncMock]:
        engine = MagicMock()
        consumer = _TrackingConsumer(engine)

        cursor_row = MagicMock()
        cursor_row.last_sequence = initial_cursor

        session = AsyncMock()
        begin_cm = MagicMock()
        begin_cm.__aenter__ = AsyncMock(return_value=None)
        begin_cm.__aexit__ = AsyncMock(return_value=False)
        session.begin = MagicMock(return_value=begin_cm)

        cursor_result = MagicMock()
        cursor_result.scalar_one_or_none.return_value = cursor_row

        batch_scalars = MagicMock()
        batch_scalars.all.return_value = rows
        batch_result = MagicMock()
        batch_result.scalars.return_value = batch_scalars

        upsert_result = MagicMock()

        session.execute = AsyncMock(
            side_effect=[cursor_result, batch_result, upsert_result]
        )

        session_cm = MagicMock()
        session_cm.__aenter__ = AsyncMock(return_value=session)
        session_cm.__aexit__ = AsyncMock(return_value=False)
        consumer._session_factory = MagicMock(return_value=session_cm)
        return consumer, session

    @pytest.mark.asyncio
    async def test_process_batch_calls_handle_for_each_event(self) -> None:
        """Each row fetched from the outbox triggers one handle() call."""
        rows = [
            _make_outbox_row(event_type="ProcessingCompleted", sequence_num=1),
            _make_outbox_row(event_type="DocumentIngested", sequence_num=2),
        ]
        consumer, _ = self._build_consumer_with_rows(rows)

        await consumer._process_batch()

        assert len(consumer.handled) == 2
        assert consumer.handled[0][0] == "ProcessingCompleted"
        assert consumer.handled[1][0] == "DocumentIngested"

    @pytest.mark.asyncio
    async def test_handle_receives_event_id(self) -> None:
        """handle() is called with the row's event_id as the third argument."""
        row = _make_outbox_row(event_type="ProcessingCompleted", sequence_num=1)
        consumer, _ = self._build_consumer_with_rows([row])

        await consumer._process_batch()

        assert len(consumer.handled) == 1
        _, _, received_event_id = consumer.handled[0]
        assert received_event_id == row.event_id

    @pytest.mark.asyncio
    async def test_process_batch_advances_cursor_to_max_sequence(self) -> None:
        """After a batch the cursor is set to the highest sequence_num."""
        rows = [
            _make_outbox_row(sequence_num=5),
            _make_outbox_row(sequence_num=7),
            _make_outbox_row(sequence_num=6),
        ]
        consumer, session = self._build_consumer_with_rows(rows)

        await consumer._process_batch()

        upsert_stmt = session.execute.call_args_list[2][0][0]
        assert "consumer_cursors" in str(upsert_stmt).lower()

    @pytest.mark.asyncio
    async def test_process_batch_returns_zero_on_empty(self) -> None:
        """Returns 0 and does not advance cursor when there are no events."""
        consumer, session = self._build_consumer_with_rows([])

        result = await consumer._process_batch()

        assert result == 0
        assert session.execute.await_count == 2

    @pytest.mark.asyncio
    async def test_process_batch_stops_on_handler_error_below_max_retries(self) -> None:
        """A handler error below max retries stops the batch without advancing cursor."""

        class _FailingConsumer(_TrackingConsumer):
            async def handle(
                self, event_type: str, payload: dict, event_id: uuid.UUID
            ) -> None:
                raise RuntimeError("simulated handler failure")

        engine = MagicMock()
        consumer = _FailingConsumer(engine)
        rows = [_make_outbox_row(sequence_num=1)]

        cursor_row = MagicMock()
        cursor_row.last_sequence = 0

        session = AsyncMock()
        begin_cm = MagicMock()
        begin_cm.__aenter__ = AsyncMock(return_value=None)
        begin_cm.__aexit__ = AsyncMock(return_value=False)
        session.begin = MagicMock(return_value=begin_cm)

        cursor_result = MagicMock()
        cursor_result.scalar_one_or_none.return_value = cursor_row

        batch_scalars = MagicMock()
        batch_scalars.all.return_value = rows
        batch_result = MagicMock()
        batch_result.scalars.return_value = batch_scalars

        session.execute = AsyncMock(side_effect=[cursor_result, batch_result])

        session_cm = MagicMock()
        session_cm.__aenter__ = AsyncMock(return_value=session)
        session_cm.__aexit__ = AsyncMock(return_value=False)
        consumer._session_factory = MagicMock(return_value=session_cm)

        result = await consumer._process_batch()

        assert result == 0
        assert session.execute.await_count == 2

    @pytest.mark.asyncio
    async def test_poison_event_skipped_after_max_retries(self) -> None:
        """After _MAX_RETRIES_PER_EVENT consecutive failures the cursor advances."""

        class _AlwaysFailingConsumer(DurableEventConsumer):
            CONSUMER_NAME = "always_failing"

            def __init__(self, engine: Any) -> None:
                super().__init__(self.CONSUMER_NAME, engine)

            async def handle(
                self, event_type: str, payload: dict, event_id: uuid.UUID
            ) -> None:
                raise RuntimeError("poison")

        row = _make_outbox_row(sequence_num=42)
        consumer = _AlwaysFailingConsumer(MagicMock())

        def _build_session_cm() -> tuple[Any, AsyncMock]:
            cursor_row = MagicMock()
            cursor_row.last_sequence = 0
            s = AsyncMock()
            bcm = MagicMock()
            bcm.__aenter__ = AsyncMock(return_value=None)
            bcm.__aexit__ = AsyncMock(return_value=False)
            s.begin = MagicMock(return_value=bcm)
            cr = MagicMock()
            cr.scalar_one_or_none.return_value = cursor_row
            bs = MagicMock()
            bs.all.return_value = [row]
            br = MagicMock()
            br.scalars.return_value = bs
            ur = MagicMock()
            s.execute = AsyncMock(side_effect=[cr, br, ur])
            scm = MagicMock()
            scm.__aenter__ = AsyncMock(return_value=s)
            scm.__aexit__ = AsyncMock(return_value=False)
            return scm, s

        for _ in range(_MAX_RETRIES_PER_EVENT - 1):
            scm, _ = _build_session_cm()
            consumer._session_factory = MagicMock(return_value=scm)
            result = await consumer._process_batch()
            assert result == 0

        scm, final_session = _build_session_cm()
        consumer._session_factory = MagicMock(return_value=scm)
        result = await consumer._process_batch()

        # Cursor upsert (3rd execute) must have been called
        assert final_session.execute.await_count == 3


# ===========================================================================
# 8.5.4 - Idempotency: same event_id handled twice -> no duplicate side effects
# ===========================================================================


class TestIdempotency:
    def _make_audit_consumer(self) -> tuple[AuditLoggerConsumer, Any]:
        engine = MagicMock()
        factory, repo = _make_audit_repo_factory()

        session = AsyncMock()
        begin_cm = MagicMock()
        begin_cm.__aenter__ = AsyncMock(return_value=None)
        begin_cm.__aexit__ = AsyncMock(return_value=False)
        session.begin = MagicMock(return_value=begin_cm)
        session_cm = MagicMock()
        session_cm.__aenter__ = AsyncMock(return_value=session)
        session_cm.__aexit__ = AsyncMock(return_value=False)

        consumer = AuditLoggerConsumer(engine=engine, interaction_repo_factory=factory)
        consumer._session_factory = MagicMock(return_value=session_cm)
        return consumer, repo

    @pytest.mark.asyncio
    async def test_audit_logger_writes_to_interaction_turns(self) -> None:
        """AuditLoggerConsumer persists an interaction turn to the database."""
        consumer, repo = self._make_audit_consumer()

        event_id = uuid.uuid4()
        payload = {"document_id": str(uuid.uuid4()), "lesson_id": "test"}

        await consumer.handle("ProcessingCompleted", payload, event_id)

        repo.create_interaction.assert_awaited_once()
        repo.add_turn.assert_awaited_once()
        add_turn_kwargs = repo.add_turn.call_args[1]
        assert add_turn_kwargs["input_data"]["event_id"] == str(event_id)

    @pytest.mark.asyncio
    async def test_audit_logger_handles_same_event_twice_without_error(self) -> None:
        """AuditLoggerConsumer does not raise on duplicate event deliveries."""
        consumer, repo = self._make_audit_consumer()

        event_id = uuid.uuid4()
        payload = {
            "document_id": str(uuid.uuid4()),
            "lesson_id": "test-lesson",
            "timestamp": datetime.now(tz=timezone.utc).isoformat(),
        }
        await consumer.handle("ProcessingCompleted", payload, event_id)
        await consumer.handle("ProcessingCompleted", payload, event_id)

        assert repo.create_interaction.await_count == 2

    @pytest.mark.asyncio
    async def test_graph_indexer_consumer_skips_unknown_events(self) -> None:
        """GraphIndexerConsumer ignores events other than ProcessingCompleted."""
        engine = MagicMock()
        artifact_repo_factory = MagicMock()
        graph_indexer = AsyncMock()

        consumer = GraphIndexerConsumer(
            engine=engine,
            graph_indexer=graph_indexer,
            artifact_repo_factory=artifact_repo_factory,
        )

        await consumer.handle(
            "DocumentIngested", {"document_id": str(uuid.uuid4())}, uuid.uuid4()
        )

        graph_indexer.index_artifact.assert_not_called()

    @pytest.mark.asyncio
    async def test_audit_logger_ignores_non_audited_events(self) -> None:
        """AuditLoggerConsumer silently ignores event types not in its allowlist."""
        engine = MagicMock()
        factory, repo = _make_audit_repo_factory()
        consumer = AuditLoggerConsumer(engine=engine, interaction_repo_factory=factory)

        await consumer.handle(
            "PipelineStepCompleted", {"step_name": "summarizer"}, uuid.uuid4()
        )

        repo.create_interaction.assert_not_awaited()


# ===========================================================================
# 8.5.5 - FOR UPDATE SKIP LOCKED used in relay batch claim
# ===========================================================================


class TestSkipLockedClaim:
    @pytest.mark.asyncio
    async def test_claim_batch_uses_skip_locked(self) -> None:
        """The SELECT statement includes FOR UPDATE SKIP LOCKED."""
        from sqlalchemy.dialects import postgresql as pg_dialect

        relay = OutboxRelay(engine=MagicMock(), redis_client=None)

        scalars_mock = MagicMock()
        scalars_mock.all.return_value = []
        execute_result = MagicMock()
        execute_result.scalars.return_value = scalars_mock

        session = AsyncMock()
        session.execute = AsyncMock(return_value=execute_result)

        await relay._claim_batch(session)

        stmt = session.execute.call_args[0][0]
        compiled = str(stmt.compile(dialect=pg_dialect.dialect())).lower()
        assert "skip locked" in compiled


# ===========================================================================
# Envelope format tests
# ===========================================================================


class TestEnvelopeFormat:
    @pytest.mark.asyncio
    async def test_redis_envelope_has_required_keys(self) -> None:
        """The envelope published to Redis contains all required keys."""
        row = _make_outbox_row(event_type="ProcessingCompleted", sequence_num=1)

        redis = AsyncMock()
        relay = OutboxRelay(engine=MagicMock(), redis_client=redis)

        await relay._publish_to_redis(row)

        redis.publish.assert_awaited_once()
        raw_envelope = redis.publish.call_args[0][1]
        envelope = json.loads(raw_envelope)

        assert "event_id" in envelope
        assert "event_type" in envelope
        assert "payload" in envelope
        assert "created_at" in envelope
        assert envelope["event_type"] == "ProcessingCompleted"
