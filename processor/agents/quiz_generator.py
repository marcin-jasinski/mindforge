"""
Quiz generator agent — creates self-assessment quizzes from lesson content.
"""
from __future__ import annotations

import logging
import re

from processor.llm_client import LLMClient

log = logging.getLogger(__name__)

SYSTEM_PROMPT = """\
Jesteś ekspertem od tworzenia materiałów edukacyjnych. Twoim zadaniem jest \
stworzenie quizu sprawdzającego zrozumienie lekcji z kursu o budowaniu \
aplikacji z generatywną AI.

Struktura quizu:

## Quiz: [Tytuł lekcji]

### Pytania wielokrotnego wyboru (5 pytań)
Każde pytanie ma 4 opcje (A-D), dokładnie 1 poprawna.
Mieszaj poziomy trudności: 2 łatwe, 2 średnie, 1 trudne.
Format:
**1. Treść pytania?**
- A) opcja
- B) opcja
- C) opcja
- D) opcja

### Prawda/Fałsz (3 pytania)
Format:
**6. Stwierdzenie do oceny.**
- [ ] Prawda
- [ ] Fałsz

### Pytania otwarte (2 pytania)
Wymagające wyjaśnienia koncepcji lub opisania procesu.
Format:
**9. Treść pytania?**

---

## Odpowiedzi

### Wielokrotnego wyboru
1. **B** — Wyjaśnienie dlaczego B jest poprawne (1-2 zdania)
[itd.]

### Prawda/Fałsz
6. **Prawda** — Wyjaśnienie (1-2 zdania)
[itd.]

### Pytania otwarte
9. **Wzorcowa odpowiedź:** [2-4 zdania]
[itd.]

Zasady:
- Pytania muszą testować ZROZUMIENIE, nie odtwarzanie tekstu
- Uwzględnij pytania o praktyczne zastosowania
- Dystraktory (błędne opcje) powinny być wiarygodne
- Odpowiadaj po polsku
- NIE dodawaj żadnych komentarzy poza strukturą quizu\
"""


def generate_quiz(
    content: str,
    summary: str,
    title: str,
    source_filename: str,
    llm: LLMClient,
    model: str,
) -> str:
    """Generate a self-assessment quiz for the lesson.

    Returns:
        Markdown string with quiz questions and answers separated by ---.
    """
    lesson_number = _extract_lesson_number(source_filename)

    user_message = (
        f"# Lekcja: {title} ({lesson_number})\n\n"
        f"## Treść lekcji\n{content}\n\n"
        f"## Podsumowanie lekcji\n{summary}\n"
    )

    log.info("Generating quiz for: %s", title)

    quiz_md = llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        temperature=0.5,
    )

    log.info("Quiz generated for %s: %d chars", title, len(quiz_md))
    return quiz_md


def _extract_lesson_number(filename: str) -> str:
    match = re.match(r"s(\d+)e(\d+)", filename, re.IGNORECASE)
    if match:
        return f"S{match.group(1).zfill(2)}E{match.group(2).zfill(2)}"
    return "unknown"
