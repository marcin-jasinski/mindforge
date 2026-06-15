## Test Writing

### Test Behavior
Focus on what code does, not how it does it, to allow safe refactoring.

### Clear Names
Use descriptive names explaining what's tested and expected (`shouldReturnErrorWhenUserNotFound`).

### Mock External Dependencies
Isolate tests by mocking databases, APIs, and external services.

### Fast Execution
Keep unit tests fast (milliseconds) so developers run them frequently.

### Risk-Based Testing
Prioritize testing based on business criticality and likelihood of bugs.

### Balance Coverage and Velocity
Adjust test coverage based on project needs and team workflow.

### Critical Path Focus
Ensure core user workflows and critical business logic are well-tested.

### Appropriate Depth
Match edge case testing to the risk profile of the code.

---

## Java Testing Conventions (MindForge)

### Test Discovery and Structure

```
src/test/java/.../unit/        — no I/O, no Spring context, fast; run on every PR
src/test/java/.../integration/ — real DB via Testcontainers; @Tag("integration")
src/test/java/.../e2e/         — full stack; @Tag("e2e")
```

Run without DB: `mvn test -Dgroups=\!integration`

### Unit Tests (No Spring Context)

Domain and application layer unit tests must **not** load a Spring context. Use plain Mockito:

```java
// CORRECT: no Spring annotation needed
class QuizServiceTest {
    private final StubAIGateway gateway = new StubAIGateway();
    private final QuizSessionPort sessions = Mockito.mock(QuizSessionPort.class);

    @Test
    void shouldReturnEvaluationWhenAnswerIsCorrect() {
        QuizService service = new QuizService(gateway, sessions);
        // ...
        assertThat(result.isCorrect()).isTrue();
    }
}

// AVOID: loading Spring context for unit tests
@SpringBootTest  // ❌ not needed for domain/application logic
class QuizServiceTest { ... }
```

### LLM Test Double

Use `StubAIGateway` for deterministic LLM responses. Never mock at the Spring AI `ChatClient` call level:

```java
StubAIGateway gateway = new StubAIGateway();
gateway.setResponse("*", "expected summary text");

// Wildcard "*" matches any prompt; or use specific patterns to match different agents
```

### Mockito Conventions

Use `Mockito.mock()` for all port dependencies:

```java
private QuizService makeQuizService(AIGateway gateway, QuizSessionPort sessions) {
    return new QuizService(
        gateway != null ? gateway : new StubAIGateway(),
        sessions != null ? sessions : Mockito.mock(QuizSessionPort.class)
    );
}
```

### Test Factory Methods

Use static `make*` factory methods (not `@BeforeEach` fixtures) for domain objects and services. Accept parameters only for the fields under test:

```java
static DocumentArtifact makeArtifact(UUID documentId, String lessonId) {
    return new DocumentArtifact(
        documentId != null ? documentId : UUID.randomUUID(),
        lessonId != null ? lessonId : "test-lesson"
        // ... sensible defaults
    );
}

// In test:
DocumentArtifact artifact = makeArtifact(null, "custom-lesson");
```

### Integration Tests

Use `@Testcontainers` with real PostgreSQL/Neo4j containers. Mark with `@Tag("integration")`. Never use production credentials in tests.

```java
@Tag("integration")
@Testcontainers
class DocumentRepositoryIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    // ...
}
```

### AssertJ Assertions

Use AssertJ fluent assertions throughout. Never use JUnit's bare `assertEquals`:

```java
// CORRECT
assertThat(document).extracting(Document::status).isEqualTo(Status.DONE);
assertThat(result.errors()).isEmpty();

// AVOID
assertEquals(Status.DONE, document.getStatus());  // ❌ less readable
```
