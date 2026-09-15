package dev.mindforge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.KnowledgeBase;

/** Knowledge bases, with their document and page counts derived at read. */
public interface KnowledgeBaseRepository {

    KnowledgeBase insert(KnowledgeBase knowledgeBase);

    Optional<KnowledgeBase> findById(UUID kbId);

    List<KnowledgeBase> listByOwner(UUID ownerId);

    KnowledgeBase update(UUID kbId, String name, String description);

    /**
     * Deletes the knowledge base, and by cascade everything in it, unless a run holds its lease.
     *
     * @return false, having deleted nothing, while a run is active
     */
    boolean deleteIfIdle(UUID kbId);
}
