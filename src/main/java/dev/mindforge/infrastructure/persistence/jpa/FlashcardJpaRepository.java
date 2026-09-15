package dev.mindforge.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.FlashcardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FlashcardJpaRepository extends JpaRepository<FlashcardEntity, FlashcardEntity.Key> {

    List<FlashcardEntity> findByKnowledgeBaseIdAndPageIdIn(UUID knowledgeBaseId, Collection<UUID> pageIds);

    /** An identical card inserted concurrently wins; this one is dropped (T27). */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO flashcards (knowledge_base_id, card_id, page_id, section_anchor, card_type, front, back,"
        + " source_hash, due_at) VALUES (:knowledgeBaseId, :cardId, :pageId, :sectionAnchor, :cardType, :front, :back,"
        + " :sourceHash, :dueAt) ON CONFLICT (knowledge_base_id, card_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(UUID knowledgeBaseId, String cardId, UUID pageId, String sectionAnchor, String cardType,
                       String front, String back, String sourceHash, Instant dueAt);

    /** A returned card follows its section and hash; a retired one is revived with its history, due now. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FlashcardEntity f SET f.sectionAnchor = :sectionAnchor, f.sourceHash = :sourceHash,"
        + " f.dueAt = CASE WHEN f.retiredAt IS NULL THEN f.dueAt ELSE :now END, f.retiredAt = NULL"
        + " WHERE f.knowledgeBaseId = :knowledgeBaseId AND f.cardId = :cardId")
    int refresh(UUID knowledgeBaseId, String cardId, String sectionAnchor, String sourceHash, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FlashcardEntity f SET f.retiredAt = :now WHERE f.knowledgeBaseId = :knowledgeBaseId"
        + " AND f.pageId = :pageId AND f.retiredAt IS NULL AND f.cardId NOT IN :kept")
    int retireOthers(UUID knowledgeBaseId, UUID pageId, Collection<String> kept, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FlashcardEntity f SET f.retiredAt = :now WHERE f.knowledgeBaseId = :knowledgeBaseId"
        + " AND f.pageId = :pageId AND f.retiredAt IS NULL")
    int retireAll(UUID knowledgeBaseId, UUID pageId, Instant now);

    String DUE = "SELECT f.* FROM flashcards f JOIN wiki_pages p"
        + "   ON p.knowledge_base_id = f.knowledge_base_id AND p.page_id = f.page_id"
        + " WHERE f.knowledge_base_id = :knowledgeBaseId AND f.retired_at IS NULL AND f.due_at <= :now"
        + " AND NOT EXISTS (SELECT 1 FROM page_supersessions s JOIN wiki_pages superseding"
        + "   ON superseding.knowledge_base_id = s.knowledge_base_id AND superseding.page_id = s.superseding_page_id"
        + "   WHERE s.knowledge_base_id = :knowledgeBaseId AND s.superseded_page_id = f.page_id"
        + "   AND s.section_anchor = f.section_anchor)";

    @Query(value = DUE + " ORDER BY f.due_at", nativeQuery = true)
    List<FlashcardEntity> findDue(UUID knowledgeBaseId, Instant now);

    @Query(value = DUE + " AND f.page_id IN (:pageIds) ORDER BY f.due_at", nativeQuery = true)
    List<FlashcardEntity> findDueOfPages(UUID knowledgeBaseId, Collection<UUID> pageIds, Instant now);
}
