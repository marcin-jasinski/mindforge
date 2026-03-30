"""
LLM client for the lesson processor.

Removes operator-specific sanitization (we want to preserve markdown formatting in summaries).

Public surface:
  LLMClient       — synchronous HTTP client (pipeline / CLI)
  AsyncLLMClient  — async HTTP client (FastAPI routes, Discord cog handlers)
  LLMCredentials  — secrets dataclass; never logged, never passed to routers
  load_credentials() — load secrets from .env
  Config          — legacy combined config; use AppSettings + LLMCredentials in new code
  load_config()   — legacy loader; internally delegates to load_settings() + load_credentials()
"""
from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import requests
from requests import HTTPError
from dotenv import dotenv_values

log = logging.getLogger(__name__)


@dataclass
class LLMClient:
    base_url: str
    api_key: str
    timeout_seconds: int = 180
    default_headers: dict[str, str] = field(default_factory=dict)

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        headers.update(self.default_headers)
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        return headers

    def complete(
        self,
        *,
        model: str,
        messages: list[dict[str, Any]],
        temperature: float = 0.0,
        response_format: dict[str, Any] | None = None,
    ) -> str:
        from processor import tracing

        body: dict[str, Any] = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
        }
        if response_format:
            body["response_format"] = response_format

        endpoint = f"{self.base_url.rstrip('/')}/chat/completions"
        log.info("LLM request: model=%s, messages=%d", model, len(messages))

        # Start Langfuse generation (no-op if tracing disabled)
        model_short = model.rsplit("/", 1)[-1]
        gen = tracing.start_generation(name=f"llm/{model_short}", model=model, input_data=messages)

        try:
            response = requests.post(
                endpoint,
                headers=self._headers(),
                json=body,
                timeout=self.timeout_seconds,
            )

            # Fallback: some backends don't support full json_schema
            if response.status_code >= 400 and response_format and "response_format" in response.text:
                body["response_format"] = {"type": "json_object"}
                response = requests.post(
                    endpoint,
                    headers=self._headers(),
                    json=body,
                    timeout=self.timeout_seconds,
                )

            try:
                response.raise_for_status()
            except HTTPError as exc:
                gen.error(f"HTTP {response.status_code}: {response.text[:200]}")
                raise HTTPError(
                    f"LLM request failed: {response.status_code}. Response: {response.text[:500]}",
                    response=response,
                ) from exc

            response_data = response.json()
            raw = str(response_data["choices"][0]["message"]["content"])
            usage = response_data.get("usage")

            gen.end(output=raw, usage=usage)

            # Record token spend in operational metrics
            try:
                from processor import metrics as _metrics
                _metrics.record_llm_usage(model, usage)
            except Exception:
                pass

            cleaned = _strip_thinking(raw)
            log.info("LLM response received: %d chars", len(cleaned))
            return cleaned

        except HTTPError:
            raise
        except Exception as exc:
            gen.error(str(exc))
            raise


def _strip_thinking(content: str) -> str:
    cleaned = re.sub(r"<think>.*?</think>", "", content, flags=re.IGNORECASE | re.DOTALL)
    cleaned = re.sub(r"<think>.*$", "", cleaned, flags=re.IGNORECASE | re.DOTALL)
    cleaned = re.sub(r"</?think>", "", cleaned, flags=re.IGNORECASE)
    return cleaned.strip()


# ── Async LLM client (CRITICAL-4) ───────────────────────────────────────────


@dataclass
class AsyncLLMClient:
    """Async variant of LLMClient — must be used in FastAPI routes and Discord cog handlers.

    Uses ``httpx.AsyncClient`` so event-loop threads are never blocked.
    """

    base_url: str
    api_key: str
    timeout_seconds: int = 180
    default_headers: dict[str, str] = field(default_factory=dict)

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json"}
        headers.update(self.default_headers)
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        return headers

    async def complete(
        self,
        *,
        model: str,
        messages: list[dict[str, Any]],
        temperature: float = 0.0,
        response_format: dict[str, Any] | None = None,
    ) -> str:
        import httpx
        from processor import tracing

        body: dict[str, Any] = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
        }
        if response_format:
            body["response_format"] = response_format

        endpoint = f"{self.base_url.rstrip('/')}/chat/completions"
        log.info("AsyncLLM request: model=%s, messages=%d", model, len(messages))

        model_short = model.rsplit("/", 1)[-1]
        gen = tracing.start_generation(name=f"llm/{model_short}", model=model, input_data=messages)

        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.post(
                    endpoint,
                    headers=self._headers(),
                    json=body,
                )

                # Fallback: some backends don't support full json_schema
                if response.status_code >= 400 and response_format and "response_format" in response.text:
                    body["response_format"] = {"type": "json_object"}
                    response = await client.post(
                        endpoint,
                        headers=self._headers(),
                        json=body,
                    )

            if response.status_code >= 400:
                gen.error(f"HTTP {response.status_code}: {response.text[:200]}")
                err = RuntimeError(
                    f"LLM request failed: {response.status_code}. Response: {response.text[:500]}"
                )
                err._gen_error_recorded = True  # suppress duplicate error in outer except
                raise err

            response_data = response.json()
            raw = str(response_data["choices"][0]["message"]["content"])
            usage = response_data.get("usage")

            gen.end(output=raw, usage=usage)

            try:
                from processor import metrics as _metrics
                _metrics.record_llm_usage(model, usage)
            except Exception:
                pass

            cleaned = _strip_thinking(raw)
            log.info("AsyncLLM response received: %d chars", len(cleaned))
            return cleaned

        except Exception as exc:
            if not hasattr(exc, "_gen_error_recorded"):
                gen.error(str(exc))
            raise


# ── Credentials (CRITICAL-1) ─────────────────────────────────────────────────


@dataclass
class LLMCredentials:
    """Secrets required by the LLM client and Neo4j driver.

    Never log this object.  Never pass it to FastAPI routers —
    use :class:`~processor.settings.AppSettings` for non-secret config.
    """

    api_key: str
    base_url: str = "https://openrouter.ai/api/v1"
    extra_headers: dict[str, str] = field(default_factory=dict)
    neo4j_password: str = "password"


def load_credentials(base_dir: Path | None = None) -> LLMCredentials:
    """Load secrets from ``.env`` / environment.

    Raises :exc:`ValueError` if the required ``OPENROUTER_API_KEY`` is missing.
    No side-effects (no directory creation, no tracing init).
    """
    import os

    if base_dir is None:
        base_dir = Path(__file__).resolve().parent.parent

    env_path = base_dir / ".env"
    env: dict[str, str | None] = dotenv_values(env_path) if env_path.exists() else {}

    def _get(key: str, default: str = "") -> str:
        val = env.get(key)
        if val is None:
            val = os.environ.get(key, default)
        return str(val).strip()

    api_key = _get("OPENROUTER_API_KEY")
    if not api_key:
        raise ValueError(f"Missing OPENROUTER_API_KEY in {env_path} and environment")

    base_url = _get("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1")
    neo4j_password = _get("NEO4J_PASSWORD", "password")

    extra_headers: dict[str, str] = {}
    referer = _get("OPENROUTER_HTTP_REFERER")
    app_name = _get("OPENROUTER_APP_NAME")
    if referer:
        extra_headers["HTTP-Referer"] = referer
    if app_name:
        extra_headers["X-Title"] = app_name

    return LLMCredentials(
        api_key=api_key,
        base_url=base_url,
        extra_headers=extra_headers,
        neo4j_password=neo4j_password,
    )


# ── Legacy Config / load_config ───────────────────────────────────────────────


@dataclass
class Config:
    """Legacy combined configuration object.

    .. deprecated::
        New code should use :class:`~processor.settings.AppSettings` for non-secret
        config and :class:`LLMCredentials` for secrets.  ``Config`` is kept as a
        compatibility shim so existing callers do not need to be updated all at once.

    All :class:`~processor.settings.AppSettings` fields are accessible directly
    via ``__getattr__`` delegation (e.g. ``config.model_large``).
    Polish-named directory attributes (``nowe_dir``, ``podsumowane_dir``,
    ``archiwum_dir``) are preserved as property aliases.
    """

    llm: LLMClient
    model_small: str
    model_large: str
    model_vision: str
    base_dir: Path
    nowe_dir: Path
    podsumowane_dir: Path
    archiwum_dir: Path
    flashcards_dir: Path
    quizzes_dir: Path
    diagrams_dir: Path
    knowledge_dir: Path
    state_file: Path
    enable_image_analysis: bool = True
    enable_flashcards: bool = True
    enable_quizzes: bool = True
    enable_diagrams: bool = True
    enable_knowledge_index: bool = True
    enable_validation: bool = True
    enable_tracing: bool = False
    enable_graph_rag: bool = False
    enable_embeddings: bool = True
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_username: str = "neo4j"
    neo4j_password: str = "password"
    model_embedding: str = "openai/text-embedding-3-small"

    # English aliases for the Polish directory names (MODERATE-6)
    @property
    def new_dir(self) -> Path:
        """English alias for nowe_dir."""
        return self.nowe_dir

    @property
    def summaries_dir(self) -> Path:
        """English alias for podsumowane_dir."""
        return self.podsumowane_dir

    @property
    def archive_dir(self) -> Path:
        """English alias for archiwum_dir."""
        return self.archiwum_dir


def load_config(base_dir: Path | None = None) -> Config:
    """Load combined configuration.

    Internally delegates to :func:`~processor.settings.load_settings` and
    :func:`load_credentials`.  Tracing is initialized as a side-effect when
    ``ENABLE_TRACING=true``, matching the legacy behaviour.
    """
    from processor.settings import load_settings

    settings = load_settings(base_dir)
    creds = load_credentials(base_dir)

    # Initialize Langfuse tracing as a side-effect (legacy behavior preserved).
    # New entry points should call processor.tracing.init_tracing() explicitly.
    if settings.enable_tracing:
        import os
        if base_dir is None:
            base_dir = Path(__file__).resolve().parent.parent
        env_path = base_dir / ".env"
        env = dotenv_values(env_path) if env_path.exists() else {}

        def _get(key: str, default: str = "") -> str:
            val = env.get(key)
            if val is None:
                val = os.environ.get(key, default)
            return str(val).strip()

        langfuse_secret = _get("LANGFUSE_SECRET_KEY")
        langfuse_public = _get("LANGFUSE_PUBLIC_KEY")
        langfuse_host = _get("LANGFUSE_HOST", "http://localhost:3100")
        if langfuse_secret and langfuse_public:
            from processor import tracing
            tracing.init(
                secret_key=langfuse_secret,
                public_key=langfuse_public,
                host=langfuse_host,
            )
        else:
            log.warning("ENABLE_TRACING=true but LANGFUSE_SECRET_KEY/LANGFUSE_PUBLIC_KEY missing")

    llm = LLMClient(
        base_url=creds.base_url,
        api_key=creds.api_key,
        default_headers=creds.extra_headers,
    )

    return Config(
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
