package dev.mindforge.domain.model;

import java.util.UUID;

/** Thrown when a document is removed after one of its runs queued, ran or wrote pages. */
public class DocumentNotRemovableException extends IllegalStateException {

    public DocumentNotRemovableException(UUID documentId) {
        super("Document " + documentId + " can be removed only while every one of its runs has failed");
    }
}
