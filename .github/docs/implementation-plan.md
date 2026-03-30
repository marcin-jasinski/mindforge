# MindForge Implementation Plan

Statusy aktualizować dopiero po implementacji i przejściu testów.

[X] Faza 1: Kanoniczny artefakt lekcji
Opis: Zastąpić rozproszone stringi i markdown jednym obiektem LessonArtifact, z którego powstają wszystkie pochodne materiały.
Zadania:
[X] Zadanie 1: Zbudować w `processor/models.py` kanoniczną reprezentację lekcji obejmującą metadane, oczyszczoną treść, opisy obrazów, summary, pojęcia, fiszki i mapę pojęć.
[X] Zadanie 2: Przestawić `processor/pipeline.py` oraz agentów `summarizer`, `flashcard_generator` i `concept_mapper` na structured output zwracający dane domenowe zamiast gotowego markdownu.
[X] Zadanie 3: Zapisywać artifact JSON jako źródło prawdy i renderować markdown, TSV oraz Mermaid wyłącznie z rendererów.
Oczekiwany efekt końcowy: Pipeline zapisuje spójny JSON w `state/artifacts/`, a testy i ręczna weryfikacja potwierdzają, że `summarized/`, `flashcards/` i `diagrams/` powstają z jednego artefaktu bez dublowania wejścia do kolejnych generatorów.

[X] Faza 2: Telemetria, cache i infrastruktura developerska
Opis: Dodać obserwowalność LLM, cache pobrań i pełny lokalny stack Docker potrzebny do rozwoju projektu.
Zadania:
[X] Zadanie 1: Zintegrować `processor/tracing.py` z Langfuse tak, aby każdy etap pipeline miał trace, usage, koszt, błędy i retry.
[X] Zadanie 2: Wprowadzić cache klasyfikacji linków i pobranych artykułów w `state/article_cache.json` z TTL, wersjonowaniem logiki i zapisem kosztu.
[X] Zadanie 3: Zaktualizować `env.example`, `README.md`, `Dockerfile`, `compose.yml`, `docker/` i skrypty startowe pod Neo4j, Langfuse, profile compose oraz healthchecki.
Oczekiwany efekt końcowy: `docker compose --profile observability --profile graph up --build` oraz skrypty z `scripts/` stawiają środowisko lokalne, a Langfuse pokazuje telemetrykę dla pipeline.

[X] Faza 3: Graph-RAG i oddzielenie quizu od pipeline
Opis: Oprzeć retrieval o Neo4j i usunąć zależność od statycznego `quiz.md`, przenosząc quiz do osobnego agenta.
Zadania:
[X] Zadanie 1: Zasilać Neo4j z kanonicznego `LessonArtifact` i oczyszczonej treści lekcji, z fallbackiem lexical i embedding w `graph_rag`.
[X] Zadanie 2: Zastąpić statyczny `quiz.md` osobnym `quiz_agent.py` generującym pytania i ewaluacje na podstawie retrievalu.
[X] Zadanie 3: Ograniczyć pipeline do zapisu study pack lub assessment manifest bez gotowych odpowiedzi testowych dla użytkownika.
Oczekiwany efekt końcowy: Zasobiony graf zwraca pojęcia, relacje i fragmenty dla quizu, a quiz-agent generuje pytania i ewaluacje ugruntowane w źródłach.

[X] Faza 4: Normalizacja pojęć i jakość danych
Opis: Uszczelnić model pojęć oraz dodać deterministyczną i eksperymentalną walidację jakości wyników.
Zadania:
[X] Zadanie 1: Rozszerzyć normalizację pojęć o canonical names, aliasy, confidence score, źródła definicji i reguły merge.
[X] Zadanie 2: Dodać deterministyczne walidacje spójności summary, concept map, flashcards i payloadów assessment.
[X] Zadanie 3: Raportować evale do Langfuse oraz przygotować metryki jakości dla retrievalu i pytań quizowych.
Oczekiwany efekt końcowy: Testy walidacyjne i evale wychwytują niespójności pojęć, słabe fiszki i brak grounding source zanim wynik trafi dalej.

[X] Faza 5: Backfill i rollout
Opis: Odtworzyć dane dla istniejących lekcji i przygotować bezpieczne przejście na nową architekturę artefaktów i grafu.
Zadania:
[X] Zadanie 1: Ponownie przetworzyć dwie istniejące lekcje przez nowy pipeline.
[X] Zadanie 2: Odbudować knowledge index i zasilić Neo4j na podstawie nowych artefaktów.
[X] Zadanie 3: Przygotować procedurę migracji pozostałego archiwum po potwierdzeniu poprawności na próbce.
Oczekiwany efekt końcowy: Dwie lekcje przechodzą pełen backfill, retrieval działa na nowych danych, a rollout dla reszty archiwum ma potwierdzoną ścieżkę migracji.

[X] Faza 6: Backend API i granice zaufania
Opis: Zbudować FastAPI nad pipeline i graph-RAG oraz domknąć krytyczne granice zaufania z code review.
Zadania:
[X] Zadanie 1: Dodać API z routerami `lessons`, `concepts`, `quiz`, `flashcards`, `search`, `auth` oraz dependency injection dla konfiguracji, Neo4j i `LLMClient`.
[X] Zadanie 2: Utrzymać server-authoritative quiz flow, OAuth Discord z walidacją `state`, bezpieczne cookies, sanitizację uploadów i testy dla auth, uploadu oraz quiz session.
[X] Zadanie 3: Zachować cienkie routery, zsynchronizować kontrakty z frontendem i serwować zbudowane SPA z `frontend/dist/frontend/browser`.
Oczekiwany efekt końcowy: `pytest tests/test_auth.py tests/test_upload_sanitize.py tests/test_quiz_session.py` przechodzi, `/api/health` działa, a przeglądarka nie dostaje `grounding context` ani `reference_answer`.

[X] Faza 7: Angular GUI
Opis: Dostarczyć webowy interfejs użytkownika dla dashboardu, mapy pojęć, quizu, fiszek, wyszukiwania i uploadu.
Zadania:
[X] Zadanie 1: Zbudować standalone SPA Angular 19 z lazy-loaded routes, `AuthGuard`, usługami HTTP w `frontend/src/app/core/services/` i montowaniem przez FastAPI.
[X] Zadanie 2: Wdrożyć dashboard, concept map z Cytoscape.js, quiz, flashcards, search i upload wraz z responsywnym layoutem desktop i mobile.
[X] Zadanie 3: Zachować bezpieczeństwo kontraktów klient-serwer: quiz bez kontekstu w browserze, upload przez API, SSE do powiadomień o końcu pipeline.
Oczekiwany efekt końcowy: `cd frontend && npm run build` kończy się sukcesem, interfejs działa pod `:8080`, a krytyczne ścieżki użytkownika przechodzą ręczną weryfikację end-to-end.

[ ] Faza 8: Discord Bot
Opis: Domknąć integrację Discord jako osobny runtime surface współdzielący dane z API i grafem.
Zadania:
[ ] Zadanie 1: Uporządkować cogs `quiz`, `search`, `upload`, `notifications` wokół logiki współdzielonej z `quiz_agent.py` i `graph_rag.py`.
[ ] Zadanie 2: Utrzymać autoryzację bota przez allowlisty guild, role i user oraz wiązanie interakcji z właścicielem sesji.
[ ] Zadanie 3: Dodać obsługę uploadów, przypomnień SM-2 i profil compose `discord` z pełną dokumentacją uruchomienia.
Oczekiwany efekt końcowy: `pytest tests/test_bot_auth.py tests/test_interaction_ownership.py` przechodzi, a ręczna weryfikacja `/quiz`, `/search`, `/upload` i powiadomień SR kończy się powodzeniem.

[ ] Faza 9: Rozszerzona ingestion i obsługa wielu formatów
Opis: Wzmocnić pipeline o guardrails trafności, limity rozmiaru dokumentów i obsługę formatów PDF, docx i txt obok obecnego Markdown.
Zadania:
[ ] Zadanie 1: Dodać strażnik trafności na wejściu pipeline, który waliduje spójność tematyczną nowego dokumentu z istniejącą bazą wiedzy (np. porównanie embeddingów lub pojęć z grafem) i odrzuca lub flaguje kompletnie niepowiązane treści (np. przepis na zupę w bazie o agentach AI).
[ ] Zadanie 2: Rozszerzyć `processor/tools/lesson_parser.py` o obsługę formatów PDF, docx i txt z ekstrakcją tekstu, metadanych i osadzonych obrazów.
[ ] Zadanie 3: Wprowadzić konfigurowalne limity rozmiaru dokumentu (bajty, szacowane tokeny, liczba stron dla PDF) z walidacją przed rozpoczęciem kosztownego przetwarzania LLM.
Oczekiwany efekt końcowy: Pipeline akceptuje dokumenty w wielu formatach, odrzuca treści kompletnie niepowiązane z bazą wiedzy i chroni przed kosztownym przetwarzaniem zbyt dużych dokumentów.

[ ] Faza 10: AI Gateway i abstrakcja dostawców
Opis: Scentralizować komunikację z modelami AI w dedykowanej warstwie gateway, umożliwiając przełączanie dostawców i modeli bez zmian w kodzie. Patrz: architecture-fix-plan SEVERE-10.
Zadania:
[ ] Zadanie 1: Zbudować warstwę AI Gateway (LiteLLM, AI SDK lub własny adapter) z ujednoliconym interfejsem dla completions i embeddingów, zarządzaniem połączeniami, retry i monitorowaniem.
[ ] Zadanie 2: Wyeliminować bezpośredni dostęp klienta do modelu — komunikacja z AI wyłącznie przez wyspecjalizowane endpointy API o ustalonym kształcie wejścia/wyjścia (np. `/api/quiz/answer`, `/api/search/query`, nie `/api/chat`).
[ ] Zadanie 3: Zintegrować gateway z Langfuse tak, aby śledzenie kosztów, usage i retry było automatyczne niezależnie od dostawcy.
Oczekiwany efekt końcowy: Zmiana dostawcy LLM lub modelu wymaga wyłącznie zmiany konfiguracji. Klient nigdy nie ma bezpośredniego dostępu do modelu.

[ ] Faza 11: Architektura sterowana zdarzeniami
Opis: Wprowadzić event bus, orkiestrację agentów AI i resilience dla zadań długotrwałych. Patrz: architecture-fix-plan SEVERE-11.
Zadania:
[ ] Zadanie 1: Wdrożyć wewnętrzny event bus z pub-sub dla zdarzeń domenowych (nowy dokument, zmiana stanu pipeline, zakończenie przetwarzania, aktualizacja grafu).
[ ] Zadanie 2: Zbudować framework orkiestracji wielu agentów AI z deklaratywnym grafem przepływu i komunikacją agent↔agent, zastępując sekwencyjną logikę w `pipeline.py`.
[ ] Zadanie 3: Rozszerzyć streaming zdarzeń dla klienta (SSE/WebSocket) o szczegółowe postępy przetwarzania per krok i zapewnić resilience zadań przekraczających czas życia połączenia HTTP lub procesu (durable tasks z persystencją i resumption).
Oczekiwany efekt końcowy: Pipeline i agenci komunikują się przez zdarzenia, orkiestracja jest deklaratywna, klient otrzymuje real-time updates, a zamknięcie karty przeglądarki nie powoduje utraty zadania.

[ ] Faza 12: Osobne bazy wiedzy
Opis: Umożliwić tworzenie i zarządzanie osobnymi bazami wiedzy na różne tematy (np. agenty AI, architektura oprogramowania).
Zadania:
[ ] Zadanie 1: Zaprojektować model wielodomenowej bazy wiedzy z izolacją danych per baza (oddzielne podgrafy Neo4j lub osobne bazy, oddzielne artifact storage).
[ ] Zadanie 2: Dodać CRUD baz wiedzy w API oraz selektor bazy w SPA i opcjonalnie Discord/Slack.
[ ] Zadanie 3: Powiązać strażnik trafności z Fazy 9 z kontekstem wybranej bazy wiedzy — walidacja spójności tematycznej odbywa się względem docelowej bazy, nie globalnie.
Oczekiwany efekt końcowy: Użytkownik może tworzyć osobne bazy wiedzy, dodawać do nich dokumenty i przeglądać pojęcia, fiszki i quiz w kontekście wybranej bazy.

[ ] Faza 13: Model interakcji i persystencja
Opis: Zaprojektować ustrukturyzowane interakcje między aktorami (user, agent AI, system) z pełną persystencją i audit trail. Patrz: architecture-fix-plan MODERATE-11.
Zadania:
[ ] Zadanie 1: Zaprojektować model interakcji (sessions, turns, actors, context) jako encje persystowane w bazie, nie efemeryczne obiekty w pamięci.
[ ] Zadanie 2: Wdrożyć persystencję interakcji user↔agent (quiz, wyszukiwanie, upload) oraz agent↔agent (orkiestracja pipeline, łańcuch agentów) z pełną historią i kontekstem.
[ ] Zadanie 3: Dodać audit trail dla wszystkich interakcji z aktorami, umożliwiający odtworzenie przebiegu sesji, diagnostykę i compliance.
Oczekiwany efekt końcowy: Każda interakcja z systemem ma pełną historię, możliwy jest replay i diagnostyka, audit trail spełnia wymagania traceability.

[ ] Faza 14: Rozbudowa interfejsu użytkownika
Opis: Rozszerzyć GUI o zaawansowaną wizualizację, panel administracyjny i podgląd dokumentów.
Zadania:
[ ] Zadanie 1: Rozbudować concept map o filtrowanie, klastrowanie, zoom semantyczny i nawigację po zagnieżdżonych tematach.
[ ] Zadanie 2: Zbudować admin dashboard wyświetlający stan serwisów, bazy danych, kolejki zadań i metryki systemu.
[ ] Zadanie 3: Dodać rozbudowaną listę dokumentów z podglądami treści, szczegółami artefaktu, statusem przetwarzania i historią wersji.
Oczekiwany efekt końcowy: GUI oferuje zaawansowaną nawigację po wiedzy, monitoring systemu i przegląd dokumentów bez konieczności dostępu do backendu.

[ ] Faza 15: Integracja Slack
Opis: Dodać Slack jako kolejny runtime surface z dostępem do quizu, wyszukiwania, uploadu i przypomnień SR.
Zadania:
[ ] Zadanie 1: Zbudować Slack bota ze slash commands reużywającego logikę z `quiz_agent.py`, `graph_rag.py` i pipeline.
[ ] Zadanie 2: Zaimplementować autoryzację i wiązanie sesji z workspace/user identity.
[ ] Zadanie 3: Dodać profil compose `slack` i dokumentację uruchomienia.
Oczekiwany efekt końcowy: Slack bot oferuje quiz, search, upload i powiadomienia SR, współdzieląc logikę i dane z API i Discord.

[ ] Faza 16: Export artefaktów
Opis: Umożliwić eksport przetworzonych lekcji jako czytelne strony HTML.
Zadania:
[ ] Zadanie 1: Dodać renderer HTML w `processor/renderers.py`, generujący autonomiczne strony z summary, fiszkami, mapą pojęć i diagramami.
[ ] Zadanie 2: Udostępnić endpoint API do pobierania HTML export per lekcja lub zbiorczo.
[ ] Zadanie 3: Opcjonalnie dodać sandboxowy podgląd w SPA (iframe lub nowe okno).
Oczekiwany efekt końcowy: Użytkownik może wyeksportować lekcję jako czytelną stronę HTML do offline study lub udostępnienia.

[ ] Faza 17: Dokumentacja architektury
Opis: Uzupełnić dokumentację o diagramy przepływu danych, sekwencyjne i komponentowe.
Zadania:
[ ] Zadanie 1: Przygotować diagram architektury systemu (runtime surfaces, bazy danych, kolejki, event bus) jako Mermaid lub draw.io.
[ ] Zadanie 2: Udokumentować przepływ danych end-to-end (ingestion → pipeline → artifacts → graph → API/GUI/bot) z diagramami sekwencyjnymi.
[ ] Zadanie 3: Uzupełnić `docs/architecture.md` o diagramy i utrzymywać je jako living documentation aktualizowaną przy zmianach architektonicznych.
Oczekiwany efekt końcowy: Nowy contributor może zrozumieć architekturę systemu z samej dokumentacji, bez czytania kodu.

---

Odłożone (deferred):
- Ekstrakcja wiedzy z URL — odłożone do ponownej oceny po wdrożeniu multi-format ingestion (Faza 9).