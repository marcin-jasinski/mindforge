package dev.mindforge.domain.model;

import java.util.UUID;

/** Thrown when work that needs the knowledge base's lease finds another run holding it. */
public class KnowledgeBaseBusyException extends IllegalStateException {

    public KnowledgeBaseBusyException(UUID knowledgeBaseId) {
        super("Knowledge base " + knowledgeBaseId + " is busy with another run");
    }
}
