package dev.mindforge.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;

import dev.mindforge.infrastructure.persistence.entity.WikiPageEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The health view's structural checks, each one query bound to the knowledge base. */
public interface WikiHealthJpaRepository extends Repository<WikiPageEntity, UUID> {

    interface LinkRow {
        String getSourcePath();
        String getTargetPath();
        String getLivePath();
    }

    interface DuplicateRow {
        String getTitle();
        String getPaths();
    }

    interface SupersessionRow {
        UUID getSupersessionId();
        String getSupersededPath();
        String getSectionAnchor();
        String getSupersededBody();
        String getSupersedingPath();
    }

    @Query(value = "SELECT p.path AS sourcePath, l.target_path AS targetPath, CAST(NULL AS VARCHAR) AS livePath"
        + " FROM page_links l JOIN wiki_pages p"
        + "   ON p.knowledge_base_id = l.knowledge_base_id AND p.page_id = l.source_page_id"
        + " WHERE l.knowledge_base_id = :kbId AND NOT EXISTS (SELECT 1 FROM wiki_pages t"
        + "   WHERE t.knowledge_base_id = :kbId AND t.path = l.target_path)"
        + " ORDER BY p.path, l.target_path", nativeQuery = true)
    List<LinkRow> findDanglingLinks(UUID kbId);

    @Query(value = "SELECT p.path AS sourcePath, l.target_path AS targetPath, t.path AS livePath"
        + " FROM page_links l JOIN wiki_pages p"
        + "   ON p.knowledge_base_id = l.knowledge_base_id AND p.page_id = l.source_page_id"
        + " JOIN wiki_pages t ON t.knowledge_base_id = l.knowledge_base_id"
        + "   AND split_part(t.path, '/', 2) = split_part(l.target_path, '/', 2)"
        + " WHERE l.knowledge_base_id = :kbId AND NOT EXISTS (SELECT 1 FROM wiki_pages x"
        + "   WHERE x.knowledge_base_id = :kbId AND x.path = l.target_path)"
        + " ORDER BY p.path, l.target_path", nativeQuery = true)
    List<LinkRow> findWrongDirectoryLinks(UUID kbId);

    @Query(value = "SELECT p.path FROM wiki_pages p WHERE p.knowledge_base_id = :kbId AND p.page_type = :conceptType"
        + " AND NOT EXISTS (SELECT 1 FROM page_links l WHERE l.knowledge_base_id = :kbId"
        + "   AND l.target_path = p.path AND l.source_page_id <> p.page_id)"
        + " ORDER BY p.path", nativeQuery = true)
    List<String> findOrphans(UUID kbId, String conceptType);

    @Query(value = "SELECT title AS title, string_agg(path, ',' ORDER BY path) AS paths FROM wiki_pages"
        + " WHERE knowledge_base_id = :kbId AND page_type = :conceptType"
        + " GROUP BY title HAVING COUNT(*) > 1 ORDER BY title", nativeQuery = true)
    List<DuplicateRow> findDuplicateTitles(UUID kbId, String conceptType);

    @Query(value = "SELECT s.supersession_id AS supersessionId, superseded.path AS supersededPath,"
        + " s.section_anchor AS sectionAnchor, superseded.markdown_body AS supersededBody,"
        + " superseding.path AS supersedingPath"
        + " FROM page_supersessions s JOIN wiki_pages superseded"
        + "   ON superseded.knowledge_base_id = s.knowledge_base_id AND superseded.page_id = s.superseded_page_id"
        + " LEFT JOIN wiki_pages superseding"
        + "   ON superseding.knowledge_base_id = s.knowledge_base_id AND superseding.page_id = s.superseding_page_id"
        + " WHERE s.knowledge_base_id = :kbId ORDER BY superseded.path, s.section_anchor", nativeQuery = true)
    List<SupersessionRow> findSupersessions(UUID kbId);
}
