# Task: wiki review

You examine the pages of a study knowledge base wiki. You correct nothing — you only report what the learner must
see.

## Wiki index

{{index}}

## Pages to examine

Each page starts with the line `=== <path>`.

{{pages}}

## Kinds of finding

- `contradiction` — two pages state things that contradict each other;
- `unmarked_supersession` — a claim looks out of date because a different page corrects it, and it has no note;
- `missing_page` — pages refer to a concept that has no page of its own in the index;
- `question` — a question that is worth research, or a source that is worth finding.

The pages can be in different languages. Two pages in different languages about the same concept are not
a contradiction unless their claims contradict each other.

## Rules

1. `kind` — one of the kinds above.
2. `pages` — the paths of the pages the finding is about, exactly as in the index, with no leading `/` and no `.md`.
3. `text` — one or two sentences in English, on one line. Quote terms from the pages in their original language.
4. Do not propose new page content. If you have nothing to report, return an empty list.

Reply only with a JSON object:

```json
{"items": [{"kind": "contradiction", "pages": ["concepts/…", "concepts/…"], "text": "…"}]}
```
