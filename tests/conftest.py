"""Shared pytest fixtures for all test suites.

Fixture bodies are stubs at Phase 0 and will be fleshed out in later phases:
- Phase 2: settings() → real AppSettings loaded from test .env
- Phase 3: mock_gateway() → typed StubAIGateway with configurable responses
- Phase 7: stub_retrieval() → typed StubRetrievalAdapter with fixture data
"""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest


@pytest.fixture
def settings() -> MagicMock:
    """Return a stub AppSettings instance.

    Replaced in Phase 2 with a real ``AppSettings`` loaded from a test
    environment file.
    """
    stub = MagicMock(name="AppSettings")
    # Pre-configure common attributes so tests can reference them without
    # additional setup even before Phase 2 is complete.
    stub.enable_graph = False
    stub.enable_image_analysis = False
    stub.enable_tracing = False
    stub.max_document_size_mb = 10
    stub.chunk_max_tokens = 512
    stub.chunk_min_tokens = 64
    stub.chunk_overlap_tokens = 64
    return stub


@pytest.fixture
def mock_gateway() -> AsyncMock:
    """Return a stub AIGateway.

    Replaced in Phase 3 with a typed ``StubAIGateway`` that tracks calls and
    returns configurable CompletionResult fixtures.
    """
    stub = AsyncMock(name="StubAIGateway")
    return stub


@pytest.fixture
def stub_retrieval() -> AsyncMock:
    """Return a stub RetrievalPort adapter.

    Replaced in Phase 7 with a typed ``StubRetrievalAdapter`` backed by
    in-memory fixture data.
    """
    stub = AsyncMock(name="StubRetrievalAdapter")
    return stub
