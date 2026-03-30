---
description: "Use when: planning, resuming, or executing MindForge modernization work across pipeline, API, frontend, Docker, and Discord surfaces."
name: "MindForge Modernization"
argument-hint: "Zakres prac, faza albo konkretne zadanie"
agent: "agent"
tools: [read, search, todo]
---

Pracuj nad modernizacją MindForge, korzystając z poniższych źródeł kontekstu:

- [Plan implementacji](../docs/implementation-plan.md)
- [Architektura projektu](../docs/architecture.md)
- [Instrukcje workspace](../copilot-instructions.md)
- [Deep code review](../reviews/mindforge-deep-code-review-2026-04-01.md)

Sposób pracy:

1. Zidentyfikuj fazę i zadanie z planu, których dotyczy bieżąca praca.
2. Sprawdź ograniczenia architektoniczne, bezpieczeństwa i kosztowe przed proponowaniem zmian.
3. Aktualizuj checkboxy w planie dopiero po implementacji i przejściu testów.
4. Synchronizuj kontrakty API między backendem i frontendem, jeśli zmiana dotyka obu stron.
5. Jeśli praca dotyczy quizu, auth, uploadu, fetchowania URL-i lub Discorda, uwzględnij odpowiednie testy regresyjne.

W odpowiedzi podaj:

- wybraną fazę i zadanie,
- planowane zmiany,
- sposób weryfikacji.
