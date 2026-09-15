package dev.mindforge.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.SourceCitation;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.BundleQuery;
import dev.mindforge.infrastructure.persistence.jpa.PageSourceJpaRepository;

@Transactional(readOnly = true)
public class BundleQueryAdapter implements BundleQuery {

    private final PageSourceJpaRepository sources;

    public BundleQueryAdapter(PageSourceJpaRepository sources) {
        this.sources = sources;
    }

    @Override
    public List<SourceCitation> citations(UUID kbId, Collection<UUID> pageIds) {
        return sources.findCitations(kbId, pageIds).stream()
            .map(row -> new SourceCitation(row.getPageId(), row.getDocumentId(), row.getLessonId(),
                row.getLessonTitle(), row.getSourceFilename(),
                UploadSource.CONVERSATION.name().equals(row.getUploadSource()), row.getUploadedAt()))
            .toList();
    }
}
