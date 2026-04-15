"""
Infrastructure — event publisher stub.

Full ``OutboxEventPublisher`` (Phase 8) will write INSERT rows into
``outbox_events`` within the caller's open database transaction.
This stub satisfies the ``EventPublisher`` protocol during Phases 5–7
so the composition root and tests can wire and exercise the orchestrator
before Phase 8 is implemented.
"""

from __future__ import annotations

from typing import Any


class OutboxEventPublisher:
    """No-op event publisher stub used until Phase 8 implements the outbox.

    Phase 8 will replace this with a real implementation that writes to
    the ``outbox_events`` table within the caller's transaction (the
    ``connection`` argument).  Until then, events are silently discarded
    so that the pipeline can run end-to-end without the full event system.
    """

    def __init__(self, session: Any) -> None:
        self._session = session

    async def publish_in_tx(self, event: Any, connection: Any) -> None:  # noqa: ARG002
        """Discard the event.  Phase 8 replaces this with a real INSERT."""
