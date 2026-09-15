# Zadanie: pytania quizowe

Układasz krótki quiz z poniższych stron wiki. Strony są podane w kolejności ważności — zacznij od pierwszych.

## Strony

Każda strona zaczyna się od linii `=== <ścieżka>`.

{{pages}}

## Zasady

1. Najwyżej {{count}} pytań, każde o jednej rzeczy, z odpowiedzią wynikającą z treści stron.
2. `pagePath` — ścieżka strony, z której pochodzi pytanie, dokładnie jak w linii `===`.
3. `sectionAnchor` — kotwica sekcji poziomu 1 albo `null`.
4. `question` — pytanie po polsku, bez podpowiedzi odpowiedzi.
5. `referenceAnswer` — wzorcowa odpowiedź; `groundingExcerpt` — dosłowny fragment strony, który ją uzasadnia.

Odpowiedz wyłącznie obiektem JSON:

```json
{"questions": [{"pagePath": "concepts/…", "sectionAnchor": null, "question": "…", "referenceAnswer": "…", "groundingExcerpt": "…"}]}
```
