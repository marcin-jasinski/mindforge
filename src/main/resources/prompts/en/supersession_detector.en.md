# Task: detection of out-of-date sections

New claims were just added to the wiki. Find the sections of other pages whose content these claims correct or
make invalid. The content of these sections does not change — the system adds a correction note to them.

## New claims

Each claim starts with the path of the page it was added to.

{{claims}}

## Sections to examine

Each section starts with the line `=== <path>#<anchor> — <heading>`.

{{sections}}

## Rules

1. Give only sections from the list above, with `supersededPath` and `sectionAnchor` exactly as in the `===` line.
2. `supersedingPath` — the path of the page with the new claim, exactly as in the square brackets.
3. A page cannot correct itself.
4. Give each section at most one time. The claims and the sections can be in different languages; compare them by
   meaning. If no section is out of date, return an empty list.

Reply only with a JSON object:

```json
{"proposals": [{"supersededPath": "concepts/…", "sectionAnchor": "…", "supersedingPath": "concepts/…"}]}
```
