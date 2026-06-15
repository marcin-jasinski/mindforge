# Project Vision

## Overview

MindForge is an AI-powered learning platform that transforms uploaded documents into
structured study artifacts and provides interactive knowledge assessment. A user uploads
a document (Markdown, PDF, DOCX, or TXT), the system extracts and enriches the content,
generates summaries, flashcards, and concept maps, builds a queryable knowledge graph,
and then exposes this knowledge through a web UI, an interactive quiz engine, and a Discord bot.

## Current State

- **Version**: 1.0.0 (Phase 1 — in development)
- **Status**: Active development — core pipeline and API in progress
- **Users**: Solo developer / personal learning tool
- **Tech Stack**: Java 21 / Spring Boot 3.2 / Spring AI / Angular 21 / PostgreSQL + pgvector / Neo4j / OpenRouter

## Purpose

MindForge exists to solve the personal learning problem: raw learning materials are
hard to internalize. The system automates the conversion of any document into study
artifacts (summaries, flashcards, concept maps) and provides active recall through
quizzes — all driven by AI so the learner focuses on learning, not on note-taking.

**Core value loop:**
1. Upload any document → 2. AI pipeline generates artifacts → 3. Study via quizzes & concept maps → 4. Track retention over time

## Goals

### Core System (Phases 0–13) — working learning platform
- Document ingestion + 7-agent AI pipeline (preprocessor, relevance guard, summarizer,
  flashcard generator, concept mapper, quiz generator, quiz evaluator)
- Step-fingerprint checkpointing — expensive LLM steps skipped on re-ingestion if unchanged
- Quiz engine with SM-2 spaced repetition scheduling
- Multi-turn conversational RAG chat over knowledge bases
- Full-text + semantic search (pgvector)
- Angular SPA with Cytoscape.js concept map and real-time SSE pipeline progress
- PostgreSQL + pgvector + Neo4j data stores; Flyway migrations
- Docker deployment to Railway/Render

### Post-MVP Enhancements (Phases 14–21) — layered on top of a running system
- Langfuse observability and LLM cost tracking (Phase 14)
- CLI tools for scripted pipeline and quiz workflows (Phase 15)
- Image analysis via VISION model tier (Phase 16)
- Article fetcher with SSRF-safe egress policy (Phase 17)
- Discord and Slack bot integrations for ambient learning (Phases 18–19)
- Security checklist pass + OWASP dependency audit (Phase 20)
- GitHub Actions CI/CD with Testcontainers + Playwright E2E (Phase 21)

### Long-term
- English locale prompt support alongside existing Polish
- Mobile-friendly responsive frontend improvements
- Multi-tenant hardening and Redis-backed distributed session store
- FSRS scheduling to replace SM-2

## Evolution

MindForge is built on hexagonal architecture principles with a deliberate, phased delivery
strategy — core pipeline first, then conversational features, then delivery channels.

The architecture is intentionally designed to support new document formats, AI agent types,
and runtime surfaces (new chat platforms) without modifying the core orchestrator — enabling
incremental expansion while keeping the domain layer stable and the code fully reviewable.

---
*Last Updated*: 2025-07-07
*Project Reference*: [architecture.md](./architecture.md) | [implementation-plan.md](./implementation-plan.md)
