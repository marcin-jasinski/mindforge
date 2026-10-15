# Task: wiki edit from a conversation

The user asks for a change to their wiki. Turn the instruction into a list of changes. Find the pages it is about
in the index — the user usually names a topic, not a path.

## Wiki index

{{index}}

## User instruction

<instruction>
{{instruction}}
</instruction>

## Quoted answer (if the user asks to save it)

<answer>
{{quotedAnswer}}
</answer>

## Kinds of change

- `claim` — content to add or correct on a concept page: `text` (the claim), `title` (the concept name, one line,
  1–200 characters) and `path` — the path of an existing page `concepts/<name>`, or `null` for a new page.
- `delete` — removal of a page: `path` of an existing page `concepts/<name>`.
- `retitle` — a new title for a page: `path` of an existing page `concepts/<name>` and the new `title` (one line,
  1–200 characters).

Only `concepts/` pages can be deleted or retitled. Give paths with no leading `/` and no `.md`.
If the instruction asks for no change to the wiki, return an empty list.

## Language

First, detect the language of the target page in the index. Write `text` and `title` in that language. For a new
page, use the language of the quoted answer, or else the language of the instruction.

Reply only with a JSON object:

```json
{"items": [{"kind": "claim", "text": "…", "title": "…", "path": null}, {"kind": "delete", "path": "concepts/…"}]}
```
