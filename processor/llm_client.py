"""
LLM client for the lesson processor.

Removes operator-specific sanitization (we want to preserve markdown formatting in summaries).
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
        body: dict[str, Any] = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
        }
        if response_format:
            body["response_format"] = response_format

        endpoint = f"{self.base_url.rstrip('/')}/chat/completions"
        log.info("LLM request: model=%s, messages=%d", model, len(messages))

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
            raise HTTPError(
                f"LLM request failed: {response.status_code}. Response: {response.text[:500]}",
                response=response,
            ) from exc

        raw = str(response.json()["choices"][0]["message"]["content"])
        cleaned = _strip_thinking(raw)
        log.info("LLM response received: %d chars", len(cleaned))
        return cleaned


def _strip_thinking(content: str) -> str:
    cleaned = re.sub(r"<think>.*?</think>", "", content, flags=re.IGNORECASE | re.DOTALL)
    cleaned = re.sub(r"<think>.*$", "", cleaned, flags=re.IGNORECASE | re.DOTALL)
    cleaned = re.sub(r"</?think>", "", cleaned, flags=re.IGNORECASE)
    return cleaned.strip()


@dataclass
class Config:
    llm: LLMClient
    model_small: str
    model_large: str
    base_dir: Path
    nowe_dir: Path
    podsumowane_dir: Path
    archiwum_dir: Path
    state_file: Path


def load_config(base_dir: Path | None = None) -> Config:
    if base_dir is None:
        base_dir = Path(__file__).resolve().parent.parent

    env_path = base_dir / ".env"
    env = dotenv_values(env_path)

    api_key = str(env.get("OPENROUTER_API_KEY", "")).strip()
    if not api_key:
        raise ValueError(f"Missing OPENROUTER_API_KEY in {env_path}")

    base_url = str(env.get("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1")).strip()
    model_small = str(env.get("MODEL_SMALL", "openai/gpt-4o-mini")).strip()
    model_large = str(env.get("MODEL_LARGE", "openai/gpt-4o")).strip()

    extra_headers: dict[str, str] = {}
    referer = str(env.get("OPENROUTER_HTTP_REFERER", "")).strip()
    app_name = str(env.get("OPENROUTER_APP_NAME", "")).strip()
    if referer:
        extra_headers["HTTP-Referer"] = referer
    if app_name:
        extra_headers["X-Title"] = app_name

    llm = LLMClient(
        base_url=base_url,
        api_key=api_key,
        default_headers=extra_headers,
    )

    nowe_dir = base_dir / "new"
    podsumowane_dir = base_dir / "summarized"
    archiwum_dir = base_dir / "archive"
    state_file = base_dir / "state" / "processed.json"

    # Ensure directories exist
    for d in [nowe_dir, podsumowane_dir, archiwum_dir, state_file.parent]:
        d.mkdir(parents=True, exist_ok=True)

    return Config(
        llm=llm,
        model_small=model_small,
        model_large=model_large,
        base_dir=base_dir,
        nowe_dir=nowe_dir,
        podsumowane_dir=podsumowane_dir,
        archiwum_dir=archiwum_dir,
        state_file=state_file,
    )
