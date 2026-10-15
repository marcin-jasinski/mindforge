package dev.mindforge.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.PageGraph;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSource;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.persistence.entity.WikiPageEntity;
import dev.mindforge.infrastructure.persistence.jpa.PageLinkJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageRevisionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageSourceJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.PageSupersessionJpaRepository;
import dev.mindforge.infrastructure.persistence.jpa.WikiPageJpaRepository;
import dev.mindforge.infrastructure.persistence.mapper.WikiEntityMapper;

/** Every write appends a revision stamped with its run; links are re-derived from the body on every body write. */
@Transactional
public class WikiStoreAdapter implements WikiStore {

    private final WikiPageJpaRepository pages;
    private final PageRevisionJpaRepository revisions;
    private final PageLinkJpaRepository links;
    private final PageSourceJpaRepository sources;
    private final PageSupersessionJpaRepository supersessions;
    private final WikiEntityMapper mapper;

    public WikiStoreAdapter(WikiPageJpaRepository pages, PageRevisionJpaRepository revisions,
                            PageLinkJpaRepository links, PageSourceJpaRepository sources,
                            PageSupersessionJpaRepository supersessions, WikiEntityMapper mapper) {
        this.pages = pages;
        this.revisions = revisions;
        this.links = links;
        this.sources = sources;
        this.supersessions = supersessions;
        this.mapper = mapper;
    }

    @Override
    public Optional<WikiPage> findByPath(UUID kbId, String path) {
        return pages.findByKnowledgeBaseIdAndPath(kbId, path).map(mapper::toDomain);
    }

    @Override
    public Optional<WikiPage> findById(UUID kbId, UUID pageId) {
        return pages.findByKnowledgeBaseIdAndPageId(kbId, pageId).map(mapper::toDomain);
    }

    @Override
    public List<WikiPage> findByPaths(UUID kbId, Collection<String> paths) {
        return pages.findByKnowledgeBaseIdAndPathIn(kbId, paths).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<IndexEntry> listIndex(UUID kbId) {
        return pages.listIndex(kbId).stream().map(mapper::toIndexEntry).toList();
    }

    @Override
    public List<WikiPage> listBodies(UUID kbId, PageType type) {
        return pages.findByKnowledgeBaseIdAndPageType(kbId, type.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<UUID> pageIdsForLesson(UUID kbId, String lessonId) {
        return pages.findPageIdsForLesson(kbId, lessonId, PageType.CONCEPT.value());
    }

    @Override
    public PageRevision savePage(UUID kbId, PageWrite write, UUID runId) {
        Optional<WikiPageEntity> live = pages.findByKnowledgeBaseIdAndPageId(kbId, write.pageId());
        if (live.isPresent() && !live.get().getPath().equals(write.path())) {
            throw new IllegalArgumentException("A page's path never changes: " + live.get().getPath());
        }
        WikiPageEntity page = live.orElseGet(WikiPageEntity::new);
        return write(kbId, page, write, page.getRevision() + 1, runId);
    }

    @Override
    public PageRevision deletePage(UUID kbId, UUID pageId, UUID runId) {
        WikiPageEntity entity = pages.findByKnowledgeBaseIdAndPageId(kbId, pageId)
            .orElseThrow(() -> new IllegalArgumentException("No live page " + pageId));
        WikiPage page = mapper.toDomain(entity);
        pages.delete(entity);
        return appendRevision(kbId, new PageRevision(pageId, page.revision() + 1, runId, page.path(), page.title(),
            page.description(), page.type(), null, now()));
    }

    @Override
    public PageRevision reinsertPage(UUID kbId, PageRevision from, UUID runId) {
        List<PageRevision> history = listRevisions(kbId, from.pageId());
        WikiPageEntity page = new WikiPageEntity();
        page.setCreatedAt(history.getFirst().createdAt());
        return write(kbId, page, from.toWrite(), history.getLast().revision() + 1, runId);
    }

    @Override
    public void addSources(UUID kbId, Collection<PageSource> rows) {
        sources.saveAll(rows.stream().map(row -> mapper.toEntity(row, kbId)).toList());
    }

    @Override
    public void addSupersessions(UUID kbId, Collection<PageSupersession> rows) {
        supersessions.saveAll(rows.stream().map(row -> mapper.toEntity(row, kbId)).toList());
    }

    @Override
    public int deleteSources(UUID kbId, UUID runId, Collection<UUID> pageIds) {
        return sources.deleteByRunAndPages(kbId, runId, pageIds);
    }

    @Override
    public int deleteSupersessionsBySuperseding(UUID kbId, UUID runId, Collection<UUID> pageIds) {
        return supersessions.deleteByRunAndSupersedingPages(kbId, runId, pageIds);
    }

    @Override
    public Optional<PageSupersession> findSupersession(UUID kbId, UUID supersessionId) {
        return supersessions.findByKnowledgeBaseIdAndSupersessionId(kbId, supersessionId).map(mapper::toDomain);
    }

    @Override
    public boolean deleteSupersession(UUID kbId, UUID supersessionId) {
        return supersessions.deleteOne(kbId, supersessionId) == 1;
    }

    @Override
    public Map<UUID, PageRevision> tipRevisions(UUID kbId, Collection<UUID> pageIds) {
        return revisions.findTips(kbId, pageIds).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toMap(PageRevision::pageId, Function.identity()));
    }

    @Override
    public List<PageRevision> revisionsByRun(UUID kbId, UUID runId) {
        return revisions.findByKnowledgeBaseIdAndIngestRunId(kbId, runId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<PageRevision> findRevision(UUID kbId, UUID pageId, int revision) {
        return revisions.findByKnowledgeBaseIdAndPageIdAndRevision(kbId, pageId, revision).map(mapper::toDomain);
    }

    @Override
    public List<PageRevision> listRevisions(UUID kbId, UUID pageId) {
        return revisions.findByKnowledgeBaseIdAndPageIdOrderByRevision(kbId, pageId).stream()
            .map(mapper::toDomain).toList();
    }

    @Override
    public List<PageLink> outboundLinks(UUID kbId, Collection<UUID> pageIds) {
        return links.findByKnowledgeBaseIdAndSourcePageIdIn(kbId, pageIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<PageLink> inboundLinks(UUID kbId, Collection<String> paths) {
        return links.findByKnowledgeBaseIdAndTargetPathIn(kbId, paths).stream().map(mapper::toDomain).toList();
    }

    @Override
    public PageGraph graph(UUID kbId) {
        return new PageGraph(listIndex(kbId), pages.listEdges(kbId).stream().map(mapper::toEdge).toList());
    }

    @Override
    public List<LiveSupersession> liveSupersessionsOf(UUID kbId, Collection<UUID> pageIds) {
        return supersessions.findLive(kbId, pageIds);
    }

    // ---------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------

    private PageRevision write(UUID kbId, WikiPageEntity page, PageWrite write, int revision, UUID runId) {
        Instant now = now();
        mapper.applyWrite(write, page);
        page.setKnowledgeBaseId(kbId);
        page.setRevision(revision);
        page.setUpdatedAt(now);
        if (page.getCreatedAt() == null) {
            page.setCreatedAt(now);
        }
        pages.save(page);

        links.deleteBySourcePage(kbId, write.pageId());
        links.saveAll(MarkdownStructure.links(write.markdownBody()).stream()
            .filter(link -> link.kind() == MarkdownStructure.LinkKind.INTERNAL)
            .map(link -> new PageLink(write.pageId(), link.targetPath(), link.fragment()))
            .distinct()
            .map(link -> mapper.toEntity(link, kbId))
            .toList());

        return appendRevision(kbId, new PageRevision(write.pageId(), revision, runId, write.path(), write.title(),
            write.description(), write.type(), write.markdownBody(), now));
    }

    private PageRevision appendRevision(UUID kbId, PageRevision revision) {
        revisions.save(mapper.toEntity(revision, kbId));
        return revision;
    }

    /** PostgreSQL keeps microseconds, so a returned revision equals the one read back. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
