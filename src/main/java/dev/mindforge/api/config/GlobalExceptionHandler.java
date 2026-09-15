package dev.mindforge.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import dev.mindforge.api.dto.response.ErrorResponse;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.AccountException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.LintAlreadyQueuedException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.RetryNotAllowedException;
import dev.mindforge.domain.model.RevertNotAllowedException;
import dev.mindforge.domain.model.UnknownLessonException;
import dev.mindforge.domain.model.UploadRejectedException;

/**
 * Maps exceptions to {@code {error, code, detail}}. Ingest failures are run state, not HTTP errors; an unexpected
 * exception is logged and answered without its details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e);
    }

    @ExceptionHandler({NotOwnerException.class, AccessDeniedException.class})
    ResponseEntity<ErrorResponse> forbidden(RuntimeException e) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e);
    }

    @ExceptionHandler(AccountException.class)
    ResponseEntity<ErrorResponse> account(AccountException e) {
        return e.conflict()
            ? error(HttpStatus.CONFLICT, "EMAIL_TAKEN", e)
            : error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", e);
    }

    @ExceptionHandler(LessonAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> lessonExists(LessonAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(HttpStatus.CONFLICT.getReasonPhrase(),
            "LESSON_EXISTS", e.getMessage(), e.lesson().lessonId(), e.lesson().title()));
    }

    @ExceptionHandler(KnowledgeBaseBusyException.class)
    ResponseEntity<ErrorResponse> busy(KnowledgeBaseBusyException e) {
        return error(HttpStatus.CONFLICT, "KNOWLEDGE_BASE_BUSY", e);
    }

    @ExceptionHandler(RevertNotAllowedException.class)
    ResponseEntity<ErrorResponse> revertNotAllowed(RevertNotAllowedException e) {
        return error(HttpStatus.CONFLICT, "REVERT_NOT_ALLOWED", e);
    }

    @ExceptionHandler(RetryNotAllowedException.class)
    ResponseEntity<ErrorResponse> retryNotAllowed(RetryNotAllowedException e) {
        return error(HttpStatus.CONFLICT, "RETRY_NOT_ALLOWED", e);
    }

    @ExceptionHandler(LintAlreadyQueuedException.class)
    ResponseEntity<ErrorResponse> lintQueued(LintAlreadyQueuedException e) {
        return error(HttpStatus.CONFLICT, "LINT_ALREADY_QUEUED", e);
    }

    @ExceptionHandler(LessonIdentityException.class)
    ResponseEntity<ErrorResponse> lessonIdentity(LessonIdentityException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "LESSON_IDENTITY", e);
    }

    @ExceptionHandler(UnknownLessonException.class)
    ResponseEntity<ErrorResponse> unknownLesson(UnknownLessonException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "UNKNOWN_LESSON", e);
    }

    @ExceptionHandler(UploadRejectedException.class)
    ResponseEntity<ErrorResponse> uploadRejected(UploadRejectedException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "UPLOAD_REJECTED", e);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "UPLOAD_TOO_LARGE", e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
            .map(field -> field.getField() + " " + field.getDefaultMessage())
            .reduce((first, second) -> first + "; " + second)
            .orElse("invalid request");
        return ResponseEntity.badRequest().body(ErrorResponse.of("Bad Request", "VALIDATION", detail));
    }

    @ExceptionHandler({DeadlineExceededException.class, AIGatewayUnavailableException.class})
    ResponseEntity<ErrorResponse> modelUnavailable(RuntimeException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", e);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse framework) {
            HttpStatusCode status = framework.getStatusCode();
            return ResponseEntity.status(status).body(ErrorResponse.of(String.valueOf(status.value()), "REQUEST_ERROR",
                e.getMessage()));
        }
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError()
            .body(ErrorResponse.of("Internal Server Error", "INTERNAL", "Something went wrong"));
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, Exception e) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.getReasonPhrase(), code, e.getMessage()));
    }
}
