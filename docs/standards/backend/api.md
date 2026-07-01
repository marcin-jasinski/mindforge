## API Design

### RESTful Principles
Use resource-based URLs with appropriate HTTP methods (GET, POST, PUT, PATCH, DELETE).

### Consistent Naming
Use lowercase, hyphenated or underscored names consistently across endpoints.

### Versioning
Implement versioning (URL path or headers) to manage breaking changes.

### Plural Nouns
Use plural nouns for resources (`/users`, `/products`).

### Limited Nesting
Keep URL nesting to 2-3 levels maximum for readability.

### Query Parameters
Use query parameters for filtering, sorting, and pagination.

### Proper Status Codes
Return appropriate HTTP status codes (200, 201, 400, 404, 500).

### Rate Limit Headers
Include rate limit information in response headers.

---

## Spring MVC Conventions

### Thin Controllers

Controller methods do only: input validation, authentication check, delegation to application service. No business logic.

```java
// CORRECT
@PostMapping("/quiz/sessions/{sessionId}/answer")
public EvaluationResponse submitAnswer(
        @PathVariable UUID sessionId,
        @Valid @RequestBody AnswerPayload payload,
        @AuthenticationPrincipal UserPrincipal user) {
    IngestionResult result = quizService.evaluateAnswer(sessionId, payload.answer(), user.userId());
    return EvaluationResponse.from(result);
}

// NEVER: business logic in controller
@PostMapping("/quiz/...")
public EvaluationResponse submitAnswer(...) {
    int score = calculateScore(payload.answer(), reference);  // ❌ business logic
}
```

### Constructor Injection

Always use constructor injection for services. Never use field injection (`@Autowired` on fields):

```java
// CORRECT
@RestController
@RequestMapping("/api/v1/quiz")
public class QuizController {
    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }
}

// NEVER
@Autowired
private QuizService quizService;  // ❌ field injection
```

### Virtual Thread Blocking

All controllers run on virtual threads (`spring.threads.virtual.enabled=true`) — blocking I/O is permitted and expected. Never introduce `CompletableFuture` chains or reactive types in controllers.

## DTO Layer

MindForge uses a three-tier object model: domain records (`domain.model`) → JPA entities (`persistence.entity`) → view DTOs (`api.dto`). Controllers never accept or return domain or entity types directly.

- `api/dto/response/` — `record` types returned from controllers. Must never expose the fields forbidden by `docs/standards/security/web-security.md` (`passwordHash`, `referenceAnswer`, `groundingContext`, `rawPrompt`, `rawCompletion`, `cost`), and never expose internal pipeline state (e.g. step checkpoints/fingerprints).
- `api/dto/request/` — `record` types accepted as `@RequestBody`, annotated with `jakarta.validation` constraints.
- `api/mapper/` — `@Mapper(componentModel = "spring")` MapStruct interfaces mapping domain → response DTO. These import domain types only; entity types never cross into the `api` package.

```java
// CORRECT
@Mapper(componentModel = "spring")
public interface DocumentDtoMapper {
    DocumentResponse toResponse(Document d);
}
```
