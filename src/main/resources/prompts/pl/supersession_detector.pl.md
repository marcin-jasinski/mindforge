# Zadanie: wykrywanie zdezaktualizowanych sekcji

Nowe twierdzenia właśnie trafiły do wiki. Wskaż sekcje innych stron, których treść te twierdzenia poprawiają lub
unieważniają. Treść tych sekcji się nie zmieni — system doda przy nich notę o poprawce.

## Nowe twierdzenia

Każde twierdzenie jest poprzedzone ścieżką strony, na którą trafiło.

{{claims}}

## Sekcje do sprawdzenia

Każda sekcja zaczyna się od linii `=== <ścieżka>#<kotwica> — <nagłówek>`.

{{sections}}

## Zasady

1. Wskazuj tylko sekcje z listy powyżej, podając `supersededPath` i `sectionAnchor` dokładnie jak w linii `===`.
2. `supersedingPath` — ścieżka strony z nowym twierdzeniem, dokładnie jak w nawiasie kwadratowym.
3. Strona nie może poprawiać samej siebie.
4. Każdą sekcję wskaż najwyżej raz. Jeśli żadna sekcja nie jest nieaktualna, zwróć pustą listę.

Odpowiedz wyłącznie obiektem JSON:

```json
{"proposals": [{"supersededPath": "concepts/…", "sectionAnchor": "…", "supersedingPath": "concepts/…"}]}
```
