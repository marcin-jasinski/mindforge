package dev.mindforge.application.service;

import java.util.List;
import java.util.UUID;

import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.domain.model.KnowledgeBaseHealth;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.TokenEstimate;
import dev.mindforge.domain.port.WikiHealthQuery;
import dev.mindforge.domain.port.WikiStore;

/** The live health view: SQL checks, dangling supersessions matched in Java, and the index against its ceiling. */
public class HealthService {

    private final WikiHealthQuery health;
    private final WikiStore wiki;

    public HealthService(WikiHealthQuery health, WikiStore wiki) {
        this.health = health;
        this.wiki = wiki;
    }

    public KnowledgeBaseHealth health(UUID kbId) {
        List<KnowledgeBaseHealth.DanglingSupersession> danglingSupersessions = health.supersessionsToCheck(kbId)
            .stream()
            .filter(row -> row.supersedingPath() == null
                || MarkdownStructure.section(row.supersededBody(), row.sectionAnchor()).isEmpty())
            .map(row -> new KnowledgeBaseHealth.DanglingSupersession(row.supersessionId(), row.supersededPath(),
                row.sectionAnchor(), row.supersedingPath()))
            .toList();
        return new KnowledgeBaseHealth(health.danglingLinks(kbId), health.wrongDirectoryLinks(kbId),
            health.orphanConcepts(kbId), health.duplicateConceptTitles(kbId), danglingSupersessions,
            TokenEstimate.of(IndexRenderer.render(wiki.listIndex(kbId))), IndexRenderer.RETRIEVAL_CEILING_TOKENS);
    }
}
