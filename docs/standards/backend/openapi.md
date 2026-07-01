## OpenAPI

MindForge generates its OpenAPI spec from annotated Spring MVC controllers via `springdoc-openapi-starter-webmvc-ui`. The spec is the contract: the Angular frontend generates its TypeScript types from it via `openapi-typescript` rather than hand-writing them.

### Contract-First Workflow

1. Annotate the controller method and its DTOs.
2. Start the app and fetch the generated spec from `GET /v3/api-docs` (human-readable UI at `/swagger-ui.html`).
3. Run `openapi-typescript` against that endpoint to regenerate the frontend's TypeScript types.
4. Never hand-maintain a duplicate spec file or duplicate frontend types — the backend annotations are the single source of truth.

### Annotation Conventions

- `@Operation(summary = "...")` on every controller method — one sentence, imperative.
- `@ApiResponse(responseCode = "...", description = "...")` for each distinct status code the endpoint can return (200/201, 400, 401, 403, 404).
- `@Schema(description = "...")` on request/response DTO fields where the name alone doesn't convey meaning (e.g. constraints, units, format).
- Never annotate a field with `@Schema` if that field should not exist in the DTO at all — fix the DTO, don't document around a leak.

### Forbidden Fields

The same fields forbidden from API responses generally (`docs/standards/security/web-security.md`) are forbidden from OpenAPI schemas: `passwordHash`, `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion`, `cost`. If a field can't appear in the JSON response, it can't appear in the schema either — a `@Schema`-documented forbidden field is a leak with extra steps.

### Generation Command

```bash
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/app/api/schema.ts
```

Run this after any controller or DTO change that alters the contract; commit the regenerated `schema.ts` alongside the backend change.
