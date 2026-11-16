---
id: Q08
status: answered
impact: low
blocks: [W02]
answered_by: docs/wayfinder/review-tickets/R08-api-is-unversioned.md#resolution; src/main/java/dev/mindforge/api/controller/AuthController.java
---
## Question
The plan names `/api/auth/login` and `/api/auth/register`; the code serves `/api/v1/auth/login` and
`/api/v1/auth/register`. Which paths does the limiter cover?

## Context
- Plan 20.3 text predates R08, which versioned the API under `/api/v1`. `AuthController` is
  `@RequestMapping("/api/v1/auth")`; `SecurityConfig` permits `/api/v1/auth/register` and `/api/v1/auth/login`.
- No `/api/auth/**` route exists.

## Known options
- A: The limiter covers `POST /api/v1/auth/login` and `POST /api/v1/auth/register` (the routes that exist).

## Constraints
- R08 resolution: every endpoint is under `/api/v1`. Answer: A; record the path correction in the plan's "As built".
