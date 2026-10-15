# Task: link insertion

Examine the wiki pages and propose links: phrases that are already in the text of a page and are about a different
page from the index. You do not change the text — the system only wraps the phrase in a link.

## Pages you can link to

{{index}}

## Pages to examine

Each page starts with the line `=== <path>`.

{{pages}}

## Rules

1. `pagePath` — the path of the examined page, exactly as in the `===` line.
2. `phrase` — a phrase copied word for word from the text of the page, on one line, with no `[` or `]` characters.
   Not from headings, existing links, code or code blocks. The pages can be in different languages; the phrase stays
   in the language of the examined page.
3. `targetPath` — the path of the target page from the index as `concepts/<name>` or `sources/<name>` (no leading
   `/` and no `.md`). Never the same page.
4. `fragment` — the anchor of a level-1 section of the target page, or `null`.

Reply only with a JSON object:

```json
{"insertions": [{"pagePath": "concepts/…", "phrase": "…", "targetPath": "concepts/…", "fragment": null}]}
```
