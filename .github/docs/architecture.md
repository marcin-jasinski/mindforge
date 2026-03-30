# MindForge Architecture

## 1. Mission and Operating Model

MindForge zamienia materiały lekcyjne w kanoniczne artefakty nauki, a następnie udostępnia je przez pipeline, REST API, Angular SPA, Discord i interaktywny quiz-agent. Projekt jest dziś localhost-first, ale bar architektoniczny powinien odpowiadać systemowi, który da się bezpiecznie udostępnić jako open source lub uruchomić dla innych użytkowników. Oznacza to, że granice zaufania, koszt LLM i idempotencja są częścią architektury, a nie dodatkiem.

## 2. Runtime Surfaces

### 2.1 Processing Pipeline

- Entry point: `mindforge.py`
- Orkiestracja: `processor/pipeline.py`
- Odpowiedzialności: parsowanie lekcji, analiza obrazów, czyszczenie treści, pobieranie artykułów, generowanie structured outputs, składanie `LessonArtifact`, renderowanie artefaktów pochodnych, walidacja jakości, index knowledge i zapis do grafu.

### 2.2 Quiz Agent

- Entry point: `quiz_agent.py`
- Odpowiedzialności: retrieval z Neo4j, generowanie pytań, utrzymywanie `reference_answer` po stronie serwera, ocena odpowiedzi i zwrot feedbacku wraz ze źródłami.
- To docelowy mechanizm assessmentów zamiast statycznego `quiz.md`.

### 2.3 FastAPI Backend

- Entry point: `api/main.py`
- Odpowiedzialności: auth, upload/lista lekcji, koncepty, quiz sessions, spaced repetition, search, health, serwowanie SPA.
- Routery mają być cienkie; logika biznesowa trafia do `processor/`, `quiz_agent.py` lub wyspecjalizowanych helperów.

### 2.4 Angular SPA

- Entry point: `frontend/src/main.ts`
- Odpowiedzialności: dashboard, mapa pojęć, quiz, fiszki, wyszukiwanie, upload, obsługa sesji użytkownika.
- Build output: `frontend/dist/frontend/browser`, serwowany przez FastAPI.

### 2.5 Discord Bot

- Entry point: `discord_bot/bot.py`
- Odpowiedzialności: slash commands dla quizu, search, uploadu i przypomnień SR.
- Bot jest osobnym runtime surface, ale współdzieli stan i logikę domenową z resztą systemu.

## 3. Canonical Data Model and Source of Truth

- `state/artifacts/*.json` jest kanonicznym źródłem prawdy dla przetworzonych lekcji.
- `LessonArtifact` powinien zawierać co najmniej:
  - metadane lekcji,
  - oczyszczoną treść,
  - opisy obrazów,
  - structured summary,
  - fiszki,
  - dane mapy pojęć,
  - dodatkowy kontekst study pack lub assessment manifest.
- Artefakty czytelnicze są pochodne i mają powstawać wyłącznie z kanonicznego JSON:
  - `summarized/` -> markdown,
  - `flashcards/` -> TSV lub export Anki,
  - `diagrams/` -> Mermaid,
  - `knowledge/` -> eksporty czytelnicze.
- Operacyjny stan systemu jest pomocniczy, ale nie zastępuje źródła prawdy:
  - `state/processed.json`,
  - `state/article_cache.json`,
  - `state/knowledge_index.json`,
  - `state/sr_state.json`.
- Neo4j służy do retrievalu i indeksowania, a Langfuse do telemetryki. Żaden z tych systemów nie zastępuje `LessonArtifact`.

## 4. End-to-End Lesson Flow

1. `processor/tools/lesson_parser.py` odczytuje frontmatter, linki i obrazy.
2. `processor/agents/image_analyzer.py` dodaje opisy diagramów i schematów.
3. Preprocessing i cleaning usuwają szum oraz wstrzykują opisy obrazów do treści lekcji.
4. `processor/tools/article_fetcher.py` klasyfikuje i pobiera artykuły przez cache oraz politykę egress.
5. `processor/agents/summarizer.py` zwraca structured `SummaryData`.
6. `processor/agents/flashcard_generator.py` zwraca structured flashcards.
7. `processor/agents/concept_mapper.py` zwraca structured concept map.
8. `processor/pipeline.py` składa pełny `LessonArtifact`.
9. `processor/renderers.py` renderuje markdown, TSV i Mermaid.
10. `processor/tools/knowledge_index.py` aktualizuje indeks wiedzy.
11. `processor/tools/graph_rag.py` zapisuje koncepty, chunki, fakty i relacje do Neo4j.
12. `processor/validation.py` i `processor/evals.py` wykonują walidację deterministyczną i evale eksperymentalne.

Kluczowy invariant: po złożeniu `LessonArtifact` dalsze etapy mają korzystać z tego obiektu lub z jego indeksów, a nie rekonstruować stan przez parsowanie wyrenderowanego markdownu.

## 5. Retrieval and Assessment Architecture

- Retrieval jest graph-first i realizowany w `processor/tools/graph_rag.py`.
- Oczekiwany porządek fallbacków:
  1. dopasowanie grafowe i pojęciowe,
  2. fallback lexical lub full-text,
  3. embedding dopiero wtedy, gdy tańsze ścieżki nie wystarczyły.
- `quiz_agent.py` jest współdzielonym silnikiem assessmentów dla CLI, API i Discorda.
- `reference_answer` ma być generowane raz przy tworzeniu pytania i później reuse podczas oceny odpowiedzi.
- Kontrakt browser-facing jest celowo zubożony: klient dostaje treść pytania, metadane i identyfikatory sesji, ale nie grounding context ani wzorcowej odpowiedzi.

## 6. Backend and API Architecture

- `api/main.py` odpowiada za lifespan, config, opcjonalne połączenie z Neo4j, CORS, health i fallback SPA.
- `api/deps.py` centralizuje dostęp do konfiguracji, LLM clienta i drivera grafowego.
- `api/auth.py` realizuje Discord OAuth2 i wystawianie JWT w HttpOnly cookie.
- `api/schemas.py` jest warstwą kontraktu i musi być zsynchronizowane z `frontend/src/app/core/models/api.models.ts`.
- Routery mają odpowiedzialności:
  - `routers/lessons.py` -> listing i upload lekcji,
  - `routers/concepts.py` -> graf pojęć dla klientów takich jak Cytoscape,
  - `routers/quiz.py` -> start i answer dla quiz sessions,
  - `routers/flashcards.py` -> due and review dla SM-2,
  - `routers/search.py` -> retrieval przez graph-RAG.
- `api/quiz_session_store.py` jest serwerowym źródłem prawdy dla quiz sessions.

Kluczowy invariant: handler HTTP robi tylko plumbing. Wspólna logika ma być reużywalna z CLI i Discorda.

## 7. Frontend Architecture

- Frontend używa Angular standalone components i lazy-loaded routes w `frontend/src/app/app.routes.ts`.
- Logika HTTP i auth trafia do `frontend/src/app/core/`.
- Funkcje UI są rozdzielone na strony:
  - `dashboard/`,
  - `concept-map/`,
  - `quiz/`,
  - `flashcards/`,
  - `search/`,
  - `upload/`,
  - `login/`.
- Cytoscape.js obsługuje widok mapy pojęć.
- FastAPI serwuje zbudowane SPA dla monolitycznego deploymentu na porcie `8080`, a podczas lokalnego rozwoju UI działa osobny dev server Angular na `4200`.
- Domyślny język treści użytkownika pozostaje polski.

## 8. Discord Bot Architecture

- `discord_bot/bot.py` inicjalizuje klienta i ładuje cogs.
- `discord_bot/cogs/quiz.py` i `discord_bot/cogs/search.py` powinny reuse logikę z `quiz_agent.py` i `graph_rag.py`, a nie mieć własne alternatywne flow.
- `discord_bot/cogs/upload.py` wrzuca lekcje do tej samej ścieżki ingestion co API, czyli `new/`.
- `discord_bot/cogs/notifications.py` pracuje na stanie spaced repetition.
- Bot współdzieli ten sam storage, graf i modele domenowe co API oraz pipeline.

Status: bot istnieje jako runtime surface, ale nadal jest najmniej domkniętą fazą produktu i powinien być śledzony względem [implementation-plan.md](./implementation-plan.md).

## 9. Docker and Local Runtime Topology

- `Dockerfile` jest multi-stage:
  - Node 22 buduje Angular,
  - Python 3.13-slim uruchamia pipeline, API i bota.
- `compose.yml` definiuje warstwę aplikacyjną:
  - `app`,
  - `api`,
  - `quiz-agent`,
  - `discord-bot`.
- Warstwa infrastrukturalna obejmuje:
  - `neo4j`,
  - `langfuse-web`,
  - `langfuse-worker`,
  - `langfuse-postgres`,
  - `langfuse-clickhouse`,
  - `langfuse-redis`,
  - `langfuse-minio`,
  - `langfuse-minio-init`.
- Compose profiles:
  - `app`,
  - `gui`,
  - `quiz`,
  - `discord`,
  - `observability`,
  - `graph`.
- Skrypty w [scripts/STARTUP_GUIDE.md](../../scripts/STARTUP_GUIDE.md) opakowują najczęstsze kombinacje startowe dla Windows i POSIX.

Reguły deploymentowe:

- dla usług infrastrukturalnych preferować oficjalne obrazy upstream,
- utrzymywać named volumes dla Neo4j i magazynów Langfuse,
- utrzymywać spójność między build output Angulara i ścieżką serwowaną przez FastAPI,
- zapewnić możliwość postawienia pełnego środowiska developerskiego przez Compose lub wrapper scripts bez ręcznego klejenia komend.

## 10. Trust Boundaries and Security Invariants

MindForge jest dziś lokalny, ale architektura powinna być projektowana tak, jakby system miał zostać udostępniony. Traktuj poniższe zasady jako stałe invariants:

- Uploady są wrogim inputem:
  - sanitizacja nazw,
  - odrzucanie traversal, ścieżek absolutnych i drive-qualified,
  - finalny zapis tylko wewnątrz `new/`.
- Linki i URL-e obrazów są wrogim inputem:
  - każdy outbound fetch idzie przez egress validation,
  - blokowane są adresy private, loopback, link-local i metadata service,
  - redirecty muszą być rewalidowane,
  - protokoły, porty, timeouty i rozmiar odpowiedzi mają być ograniczone.
- Quiz grading jest server-authoritative:
  - browser nie dostaje grounding context ani `reference_answer`,
  - odpowiedzi są wiązane z serwerową sesją i `question_id`.
- Discord nie jest zaufanym kanałem:
  - bot musi egzekwować allowlisty,
  - widoki i modale muszą być związane z użytkownikiem, który zainicjował interakcję.
- OAuth musi walidować `state` i używać odpowiednio utwardzonych cookies.

Punkt odniesienia dla tych zasad znajduje się w [mindforge-deep-code-review-2026-04-01.md](../reviews/mindforge-deep-code-review-2026-04-01.md).

## 11. Cost and Operability Invariants

- Małe modele służą do preprocessing, duże tylko do syntezy lub oceny tam, gdzie faktycznie to potrzebne.
- Nie wolno budować promptów z całego historycznego knowledge index; trzeba pobierać tylko relewantny fragment kontekstu.
- Embeddings nie powinny być liczone na hot path bez uzasadnienia fallbackiem lub cache.
- Langfuse powinno mieć trace, usage, cost, status i retry per etap.
- Wspólny JSON state jest mutowalny cross-process. File locking i idempotencja są wymagane, gdy ten sam stan może być dotknięty przez watcher, API i Discorda.
- Graph-RAG jest opcjonalny dla samego pipeline, ale wymagany dla quizu, search, concept map i bota.

## 12. Key Architectural Decisions

- Neo4j jest docelowym graph-first backendem MVP.
- Retrieval ma używać fallbacków lexical i embedding, a nie odwrotnie.
- Statyczny `quiz.md` jest legacy i nie jest docelową powierzchnią assessmentów.
- `quiz_agent.py` jest współdzielonym silnikiem pytań i ocen dla wielu transportów.
- FastAPI serwuje zbudowane Angular SPA w monolitycznym deploymencie na `:8080`.
- Discord OAuth jest jedyną webową metodą logowania; opcjonalnie można wymusić single-user gate przez env.
- SM-2 state trzymany jest w `state/sr_state.json`, a nie w SQL.
- Bot i API współdzielą storage oraz graf bez dodatkowego API gateway pomiędzy nimi.
- Lokalna infrastruktura developerska używa pojedynczych instancji usług bez HA; priorytetem jest prostota i powtarzalność środowiska.

## 13. Entry Points Worth Reading First

- Pipeline orchestration: [processor/pipeline.py](../../processor/pipeline.py)
- Canonical models: [processor/models.py](../../processor/models.py)
- Renderers: [processor/renderers.py](../../processor/renderers.py)
- Graph retrieval: [processor/tools/graph_rag.py](../../processor/tools/graph_rag.py)
- Telemetry: [processor/tracing.py](../../processor/tracing.py)
- Quiz engine: [quiz_agent.py](../../quiz_agent.py)
- API app: [api/main.py](../../api/main.py)
- Auth flow: [api/auth.py](../../api/auth.py)
- Angular API service: [frontend/src/app/core/services/api.service.ts](../../frontend/src/app/core/services/api.service.ts)
- Discord bot entry: [discord_bot/bot.py](../../discord_bot/bot.py)