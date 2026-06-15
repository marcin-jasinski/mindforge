# Java Code Conventions

MindForge-specific Java conventions enforced across the entire `com.mindforge` package.

## File Structure

Every Java class follows this order:
1. Package declaration
2. Import statements (static imports first, then JDK stdlib, then third-party, then local)
3. Javadoc (for public API types) or inline comment (for internal types)
4. Class declaration

```java
package dev.mindforge.application.pipeline;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.mindforge.domain.model.DocumentArtifact;
```

## Constants

Class-level constants use `private static final` with SCREAMING_SNAKE_CASE:

```java
private static final int MAX_CONTENT_CHARS = 60_000;
private static final String SESSION_PREFIX = "quiz:session:";
```

## Section Dividers

Separate logical sections within a Java file with 79-character dashed comment banners:

```java
// ---------------------------------------------------------------------------
// Domain events
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Exceptions
// ---------------------------------------------------------------------------
```

## Logging

Class-level logger is named `log` (not `logger`):

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);  // CORRECT

private static final Logger logger = LoggerFactory.getLogger(MyClass.class);  // wrong name
```

## Exception Classes

Define domain-specific exception classes extending appropriate base classes. Never throw bare `RuntimeException` or `Exception` from business logic:

```java
public class DuplicateContentException extends IllegalArgumentException {
    public DuplicateContentException(UUID kbId, String contentHash) {
        super("Content already ingested in KB " + kbId + ": " + contentHash);
    }
}

public class QuizAccessDeniedException extends SecurityException { ... }

// NEVER from business logic:
throw new RuntimeException("something failed");  // ❌
```

## Records

Use `record` for:
- Domain events
- Value objects
- Result types (returned from application services)
- Agent capability descriptors

Use regular classes (with JPA annotations) only for:
- `@Entity` classes with evolving persistent state (in `infrastructure/persistence/`)

```java
public record IngestionResult(
    UUID documentId,
    String lessonId,
    boolean isDuplicate) {}
```

## Sealed Interfaces

Use `sealed interface` with `permits` for discriminated unions (e.g., agent results):

```java
public sealed interface AgentResult permits AgentResult.Success, AgentResult.Failure {
    record Success(String outputKey, Object value) implements AgentResult {}
    record Failure(String reason, Exception cause) implements AgentResult {}
}
```
