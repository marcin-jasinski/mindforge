# Zadanie: wstawianie linków

Przejrzyj strony wiki i zaproponuj linki: frazy, które już występują w treści strony i mówią o innej stronie
z indeksu. Nie zmieniasz tekstu — system tylko owija wskazaną frazę linkiem.

## Strony, do których można linkować

{{index}}

## Strony do przejrzenia

Każda strona zaczyna się od linii `=== <ścieżka>`.

{{pages}}

## Zasady

1. `pagePath` — ścieżka przeglądanej strony, dokładnie jak w linii `===`.
2. `phrase` — fraza skopiowana dosłownie z treści strony, w jednej linii, bez znaków `[` i `]`. Nie z nagłówków,
   istniejących linków, kodu ani bloków kodu.
3. `targetPath` — ścieżka strony docelowej z indeksu w postaci `concepts/<nazwa>` lub `sources/<nazwa>` (bez
   początkowego `/` i bez `.md`). Nigdy ta sama strona.
4. `fragment` — kotwica sekcji poziomu 1 strony docelowej albo `null`.

Odpowiedz wyłącznie obiektem JSON:

```json
{"insertions": [{"pagePath": "concepts/…", "phrase": "…", "targetPath": "concepts/…", "fragment": null}]}
```
