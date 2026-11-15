package dev.mindforge.slack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;

/**
 * The knowledge base a Slack command acts on (ADR 0036): the caller's only one. A command names no knowledge base, so
 * nothing is looked up by name and no selection is stored.
 */
public class SlackKnowledgeBases {

    static final String NOT_ONE = "This command needs exactly one knowledge base. "
        + "Selecting a knowledge base is not available in Slack yet.";

    private final KnowledgeBaseService knowledgeBases;

    public SlackKnowledgeBases(KnowledgeBaseService knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    /**
     * The id of the caller's only knowledge base, after the ownership check. With zero or several, or when the check
     * fails, replies with one fixed text and returns empty.
     */
    public Optional<UUID> resolveKnowledgeBase(UUID userId, SlackReply reply) {
        Optional<UUID> kbId = onlyOwnedKnowledgeBase(userId);
        if (kbId.isEmpty()) {
            reply.send(NOT_ONE);
        }
        return kbId;
    }

    private Optional<UUID> onlyOwnedKnowledgeBase(UUID userId) {
        List<KnowledgeBase> owned = knowledgeBases.list(userId);
        if (owned.size() != 1) {
            return Optional.empty();
        }
        UUID kbId = owned.getFirst().kbId();
        try {
            knowledgeBases.requireOwner(kbId, userId);
            return Optional.of(kbId);
        } catch (NotFoundException | NotOwnerException e) {
            return Optional.empty();
        }
    }
}
