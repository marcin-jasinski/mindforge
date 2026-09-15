package dev.mindforge.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.KnowledgeBaseHealth;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.port.WikiHealthQuery;
import dev.mindforge.infrastructure.persistence.jpa.WikiHealthJpaRepository;

@Transactional(readOnly = true)
public class WikiHealthQueryAdapter implements WikiHealthQuery {

    private final WikiHealthJpaRepository health;

    public WikiHealthQueryAdapter(WikiHealthJpaRepository health) {
        this.health = health;
    }

    @Override
    public List<KnowledgeBaseHealth.LinkFinding> danglingLinks(UUID kbId) {
        return health.findDanglingLinks(kbId).stream().map(WikiHealthQueryAdapter::toFinding).toList();
    }

    @Override
    public List<KnowledgeBaseHealth.LinkFinding> wrongDirectoryLinks(UUID kbId) {
        return health.findWrongDirectoryLinks(kbId).stream().map(WikiHealthQueryAdapter::toFinding).toList();
    }

    @Override
    public List<String> orphanConcepts(UUID kbId) {
        return health.findOrphans(kbId, PageType.CONCEPT.value());
    }

    @Override
    public List<KnowledgeBaseHealth.DuplicateTitle> duplicateConceptTitles(UUID kbId) {
        return health.findDuplicateTitles(kbId, PageType.CONCEPT.value()).stream()
            .map(row -> new KnowledgeBaseHealth.DuplicateTitle(row.getTitle(), List.of(row.getPaths().split(","))))
            .toList();
    }

    @Override
    public List<SupersessionToCheck> supersessionsToCheck(UUID kbId) {
        return health.findSupersessions(kbId).stream()
            .map(row -> new SupersessionToCheck(row.getSupersessionId(), row.getSupersededPath(),
                row.getSectionAnchor(), row.getSupersededBody(), row.getSupersedingPath()))
            .toList();
    }

    private static KnowledgeBaseHealth.LinkFinding toFinding(WikiHealthJpaRepository.LinkRow row) {
        return new KnowledgeBaseHealth.LinkFinding(row.getSourcePath(), row.getTargetPath(), row.getLivePath());
    }
}
