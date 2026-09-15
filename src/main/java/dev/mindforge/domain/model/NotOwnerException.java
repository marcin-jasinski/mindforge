package dev.mindforge.domain.model;

import java.util.UUID;

/** Thrown when a user reaches for a knowledge base they do not own. */
public class NotOwnerException extends SecurityException {

    public NotOwnerException(UUID knowledgeBaseId) {
        super("Not the owner of knowledge base " + knowledgeBaseId);
    }
}
