package dev.mindforge.domain.model;

import java.util.UUID;

/** Thrown when a document is retried while its latest run is not {@code FAILED}. */
public class RetryNotAllowedException extends IllegalStateException {

    public RetryNotAllowedException(UUID documentId) {
        super("Document " + documentId + " can be retried only after its latest run failed");
    }
}
