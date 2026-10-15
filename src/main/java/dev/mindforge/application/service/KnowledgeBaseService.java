package dev.mindforge.application.service;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.KnowledgeBaseBusyException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.port.KnowledgeBaseRepository;

/** A user's knowledge bases, and the one ownership check every request into a knowledge base passes. */
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBases;

    public KnowledgeBaseService(KnowledgeBaseRepository knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    public KnowledgeBase create(UUID ownerId, String name, String description) {
        return knowledgeBases.insert(new KnowledgeBase(UUID.randomUUID(), ownerId, TextRules.singleLine(name),
            description, null, 0, 0));
    }

    public List<KnowledgeBase> list(UUID ownerId) {
        return knowledgeBases.listByOwner(ownerId);
    }

    /**
     * @throws NotFoundException when there is no such knowledge base
     * @throws NotOwnerException when the user does not own it
     */
    public KnowledgeBase get(UUID kbId, UUID userId) {
        KnowledgeBase knowledgeBase = knowledgeBases.findById(kbId)
            .orElseThrow(() -> new NotFoundException("Knowledge base"));
        if (!knowledgeBase.ownerId().equals(userId)) {
            throw new NotOwnerException(kbId);
        }
        return knowledgeBase;
    }

    /** The ownership check: returns only when the user owns the knowledge base. */
    public void requireOwner(UUID kbId, UUID userId) {
        get(kbId, userId);
    }

    public KnowledgeBase update(UUID kbId, UUID userId, String name, String description) {
        requireOwner(kbId, userId);
        return knowledgeBases.update(kbId, TextRules.singleLine(name), description);
    }

    /** Deletes everything in the knowledge base. @throws KnowledgeBaseBusyException while a run is active */
    public void delete(UUID kbId, UUID userId) {
        requireOwner(kbId, userId);
        if (!knowledgeBases.deleteIfIdle(kbId)) {
            throw new KnowledgeBaseBusyException(kbId);
        }
    }
}
