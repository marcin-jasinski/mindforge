package dev.mindforge.domain.port;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.KnowledgeBaseHealth;

/** Read-only structural checks for the health view (T25). Anchors are matched in Java, over {@code MarkdownStructure}. */
public interface WikiHealthQuery {

    List<KnowledgeBaseHealth.LinkFinding> danglingLinks(UUID kbId);

    /** Dangling links whose final segment names a live page in the other directory. */
    List<KnowledgeBaseHealth.LinkFinding> wrongDirectoryLinks(UUID kbId);

    /** Live Concepts no other page links to. */
    List<String> orphanConcepts(UUID kbId);

    List<KnowledgeBaseHealth.DuplicateTitle> duplicateConceptTitles(UUID kbId);

    /** Supersessions of live pages, each with the superseded body; {@code supersedingPath} is null when that page is gone. */
    List<SupersessionToCheck> supersessionsToCheck(UUID kbId);

    record SupersessionToCheck(UUID supersessionId, String supersededPath, String sectionAnchor, String supersededBody,
                               String supersedingPath) {}
}
