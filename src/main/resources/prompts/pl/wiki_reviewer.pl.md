# Zadanie: przegląd wiki

Przeglądasz strony wiki bazy wiedzy do nauki. Niczego nie poprawiasz — zgłaszasz tylko to, co uczący się powinien
zobaczyć.

## Indeks wiki

{{index}}

## Strony do przejrzenia

Każda strona zaczyna się od linii `=== <ścieżka>`.

{{pages}}

## Rodzaje zgłoszeń

- `contradiction` — dwie strony twierdzą coś sprzecznego;
- `unmarked_supersession` — twierdzenie wygląda na nieaktualne, bo inna strona je poprawia, a nie ma przy nim noty;
- `missing_page` — strony wspominają pojęcie, które nie ma własnej strony w indeksie;
- `question` — pytanie warte zbadania albo źródło warte znalezienia.

## Zasady

1. `kind` — jeden z rodzajów powyżej.
2. `pages` — ścieżki stron, których dotyczy zgłoszenie, dokładnie jak w indeksie, bez początkowego `/` i bez `.md`.
3. `text` — jedno lub dwa zdania po polsku, w jednej linii.
4. Nie proponuj nowej treści stron. Jeśli nie masz nic do zgłoszenia, zwróć pustą listę.

Odpowiedz wyłącznie obiektem JSON:

```json
{"items": [{"kind": "contradiction", "pages": ["concepts/…", "concepts/…"], "text": "…"}]}
```
