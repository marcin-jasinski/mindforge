package dev.mindforge.domain.model;

import java.util.UUID;

/** Thrown when a full Lint is asked for while one is already queued or running in the knowledge base. */
public class LintAlreadyQueuedException extends IllegalStateException {

    public LintAlreadyQueuedException(UUID knowledgeBaseId) {
        super("A full review of knowledge base " + knowledgeBaseId + " is already queued or running");
    }
}
