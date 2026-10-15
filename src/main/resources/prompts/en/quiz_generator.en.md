# Task: quiz questions

You write a short quiz from the wiki pages below. The pages are in order of importance — start with the first.

## Pages

Each page starts with the line `=== <path>`.

{{pages}}

## Rules

1. At most {{count}} questions, each about one thing, with an answer that follows from the text of the pages.
2. `pagePath` — the path of the page that the question comes from, exactly as in the `===` line.
3. `sectionAnchor` — the anchor of a level-1 section, or `null`.
4. `question` — the question, with no hint of the answer. First, detect the language of its page, and write the
   question and `referenceAnswer` in that language.
5. `referenceAnswer` — the reference answer; `groundingExcerpt` — a word-for-word excerpt of the page that supports it.

Reply only with a JSON object:

```json
{"questions": [{"pagePath": "concepts/…", "sectionAnchor": null, "question": "…", "referenceAnswer": "…", "groundingExcerpt": "…"}]}
```
