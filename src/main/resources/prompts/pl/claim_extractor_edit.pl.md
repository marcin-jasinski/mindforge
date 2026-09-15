# Zadanie: edycja wiki z rozmowy

Użytkownik prosi o zmianę w swojej wiki. Zamień jego polecenie na listę zmian. Strony, o które chodzi, znajdź
w indeksie — użytkownik zwykle nazywa temat, a nie ścieżkę.

## Indeks wiki

{{index}}

## Polecenie użytkownika

<instruction>
{{instruction}}
</instruction>

## Cytowana odpowiedź (jeśli użytkownik prosi o jej zapisanie)

<answer>
{{quotedAnswer}}
</answer>

## Rodzaje zmian

- `claim` — treść do dopisania lub poprawienia na stronie pojęcia: `text` (twierdzenie po polsku), `title` (nazwa
  pojęcia, jedna linia, 1–200 znaków) i `path` — ścieżka istniejącej strony `concepts/<nazwa>` albo `null` dla nowej
  strony.
- `delete` — usunięcie strony: `path` istniejącej strony `concepts/<nazwa>`.
- `retitle` — zmiana tytułu strony: `path` istniejącej strony `concepts/<nazwa>` i nowy `title` (jedna linia,
  1–200 znaków).

Usuwać i zmieniać tytuły można tylko stronom `concepts/`. Ścieżki podawaj bez początkowego `/` i bez `.md`.
Jeśli polecenie nie wskazuje żadnej zmiany w wiki, zwróć pustą listę.

Odpowiedz wyłącznie obiektem JSON:

```json
{"items": [{"kind": "claim", "text": "…", "title": "…", "path": null}, {"kind": "delete", "path": "concepts/…"}]}
```
