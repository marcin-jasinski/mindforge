# Project Vision

## Overview

MindForge is an AI-powered learning platform built on a **compounding wiki**. A user uploads
a document (Markdown, PDF, DOCX or TXT) into a knowledge base. MindForge reads it and weaves
what it teaches into that knowledge base's wiki: it creates pages for new concepts, revises
existing ones, links them together, and marks where a newer source corrects an older one.
Flashcards, quizzes and answers to questions are then cut from the wiki rather than from any
single upload, so every document studied makes everything already studied richer. The wiki
exports as a portable [OKF](../wiki/llm_wiki.md) bundle.

## Current State

- **Version**: 1.0.0 (Phases 0–3 complete; Phase 3b re-cuts them for the wiki model)
- **Status**: Active development
- **Users**: Solo developer / personal learning tool
- **Tech Stack**: Java 21 / Spring Boot 4.1 / Spring AI / Angular 21 / PostgreSQL / OpenRouter

## Purpose

Raw learning materials are hard to internalize, and notes made from them rot: each new source
has to be reconciled by hand with everything already written. MindForge hands that bookkeeping
to an LLM. The learner curates sources, asks questions and studies; the LLM summarizes,
cross-links, and keeps the wiki consistent as sources accumulate.

**Core value loop:**
1. Upload any document → 2. Ingest weaves it into the knowledge base's wiki → 3. Study flashcards and quizzes cut from the wiki, and ask it questions → 4. Track retention per page as the wiki keeps growing

## Principles

- **The wiki is the substrate, not the product.** MindForge remains a learning platform; pages
  exist to be studied from.
- **The LLM is the sole author of prose.** The learner changes the wiki by telling MindForge
  what to change, never by editing text. Every change is revertible while it is the latest.
- **Grounded in what the learner uploaded.** Pages cite their sources; nothing is written from
  the model's own knowledge without a source behind it.
- **Portable.** A knowledge base leaves as a conformant OKF bundle any tool can read.

## Goals

### Core System (Phases 0–13) — working learning platform

- Document ingestion into a per-knowledge-base wiki: a typed pipeline (extract claims → resolve
  pages → write pages → check links → detect supersession) with automatic revisions and per-run revert
- Two page types — Concepts and Source Summaries — with stable paths and a rendered index
- Lint: live structural health checks, a link check inside every ingest, and an on-demand full review
  that suggests what to study or find next
- Flashcards with SM-2 spaced repetition that survive page rewrites, and server-authoritative quizzes
  targeting weak pages
- Query: multi-turn questions answered from wiki pages, with citations
- OKF bundle export
- Angular SPA: page browser, cross-link graph view, run reports with diffs and revert, a health view,
  and study and chat
- PostgreSQL as the only data store; Flyway migrations
- Docker deployment to Railway/Render

### Post-MVP Enhancements (Phases 14–21) — layered on top of a running system

- Langfuse observability and per-run LLM cost tracking (Phase 14)
- CLI tools for scripted ingestion and quizzes (Phase 15)
- Image descriptions via the VISION model tier, feeding Ingest (Phase 16)
- Article fetcher with an SSRF-safe egress policy; fetched articles become sources (Phase 17)
- Discord and Slack bots for ambient learning — `/ask`, `/quiz`, `/upload` (Phases 18–19)
- Security checklist pass + OWASP dependency audit (Phase 20)
- GitHub Actions CI/CD with Testcontainers + Playwright E2E (Phase 21)

### Long-term

- English locale prompts alongside Polish
- Mobile-friendly responsive frontend improvements
- Multi-instance deployment (lease expiry; distributed session store)
- FSRS scheduling to replace SM-2
- Shared knowledge bases (per-user flashcard schedules)

## Evolution

MindForge is built on hexagonal architecture with phased delivery: wiki and ingest first, then
study and Query, then delivery channels.

It was first designed around per-document study artifacts produced by a seven-agent pipeline.
The wiki re-cut (2026-09) replaced that model: knowledge now lives in pages that many documents
contribute to, and study material is cut from the wiki. The decisions and their reasoning are
recorded in [`docs/wayfinder/okf-wiki-map.md`](../wayfinder/okf-wiki-map.md) and ADRs 0010–0018.

New document formats remain an open extension point (`ParserRegistry`). The ingest pipeline is
deliberately closed — a fixed sequence of concrete services — because predictability matters
more than pluggability for something that rewrites a learner's knowledge base.

---
*Last Updated*: 2026-09-10
*Project Reference*: [architecture.md](./architecture.md) | [implementation-plan.md](./implementation-plan.md) | [CONTEXT.md](../../CONTEXT.md)
