package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.WikiPageEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface PageSearchJpaRepository extends Repository<WikiPageEntity, UUID> {

    /** {@code pattern} is an escaped {@code ILIKE} pattern; {@code query} goes to {@code plainto_tsquery}. */
    @Query(value = "SELECT path AS path, title AS title, description AS description, page_type AS pageType"
        + " FROM wiki_pages WHERE knowledge_base_id = :kbId"
        + " AND (title ILIKE :pattern ESCAPE '\\' OR description ILIKE :pattern ESCAPE '\\'"
        + "   OR to_tsvector('simple', markdown_body) @@ plainto_tsquery('simple', :query))"
        + " ORDER BY (title ILIKE :pattern ESCAPE '\\') DESC, title, path LIMIT :limit", nativeQuery = true)
    List<WikiPageJpaRepository.IndexRow> search(UUID kbId, String pattern, String query, int limit);
}
