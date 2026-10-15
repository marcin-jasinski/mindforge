package dev.mindforge.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * The live structural health of a knowledge base (ADR 0017, T25, T26): computed when asked, never stored and never a
 * run. The index size is shown against the retrieval ceiling past which the lexical prefilter is due.
 */
public record KnowledgeBaseHealth(
    List<LinkFinding> danglingLinks,
    List<LinkFinding> wrongDirectoryLinks,
    List<String> orphanConcepts,
    List<DuplicateTitle> duplicateConceptTitles,
    List<DanglingSupersession> danglingSupersessions,
    int indexTokens,
    int indexCeilingTokens
) {

    public KnowledgeBaseHealth {
        danglingLinks = List.copyOf(danglingLinks);
        wrongDirectoryLinks = List.copyOf(wrongDirectoryLinks);
        orphanConcepts = List.copyOf(orphanConcepts);
        duplicateConceptTitles = List.copyOf(duplicateConceptTitles);
        danglingSupersessions = List.copyOf(danglingSupersessions);
    }

    public boolean prefilterDue() {
        return indexTokens > indexCeilingTokens;
    }

    /** A link from one page to a path with no live page; {@code livePath} names the page it probably meant. */
    public record LinkFinding(String sourcePath, String targetPath, String livePath) {}

    public record DuplicateTitle(String title, List<String> paths) {

        public DuplicateTitle {
            paths = List.copyOf(paths);
        }
    }

    /** A supersession whose section is no level-1 anchor of its page, or whose superseding page is gone (null). */
    public record DanglingSupersession(UUID supersessionId, String supersededPath, String sectionAnchor,
                                       String supersedingPath) {}
}
