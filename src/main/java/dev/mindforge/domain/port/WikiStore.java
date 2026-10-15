package dev.mindforge.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.PageGraph;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSource;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.WikiPage;

/**
 * Pages, their history rows and their links. Every method takes the knowledge base first and scopes to it; the
 * store never authorizes. A deleted page is absent because its row is, so no read filters anything out.
 */
public interface WikiStore {

    Optional<WikiPage> findByPath(UUID kbId, String path);

    Optional<WikiPage> findById(UUID kbId, UUID pageId);

    List<WikiPage> findByPaths(UUID kbId, Collection<String> paths);

    List<IndexEntry> listIndex(UUID kbId);

    List<WikiPage> listBodies(UUID kbId, PageType type);

    /** Live Concepts with a source row from a document of the lesson. */
    List<UUID> pageIdsForLesson(UUID kbId, String lessonId);

    /** Inserts or revises the page, appends its revision and re-derives its links from the body. */
    PageRevision savePage(UUID kbId, PageWrite write, UUID runId);

    /** Appends a tombstone revision and deletes the page row, and with it the page's links. */
    PageRevision deletePage(UUID kbId, UUID pageId, UUID runId);

    /** Re-inserts a deleted page under its id with the content of {@code from}, as a new revision. */
    PageRevision reinsertPage(UUID kbId, PageRevision from, UUID runId);

    void addSources(UUID kbId, Collection<PageSource> sources);

    void addSupersessions(UUID kbId, Collection<PageSupersession> supersessions);

    /** Deletes the run's source rows for these pages; returns how many were deleted. */
    int deleteSources(UUID kbId, UUID runId, Collection<UUID> pageIds);

    /** Deletes the run's supersessions whose superseding page is one of these; returns how many were deleted. */
    int deleteSupersessionsBySuperseding(UUID kbId, UUID runId, Collection<UUID> pageIds);

    Optional<PageSupersession> findSupersession(UUID kbId, UUID supersessionId);

    /** Deletes one supersession; returns false when it was already gone. */
    boolean deleteSupersession(UUID kbId, UUID supersessionId);

    /** The highest revision of each page that has one, deleted pages included. */
    Map<UUID, PageRevision> tipRevisions(UUID kbId, Collection<UUID> pageIds);

    List<PageRevision> revisionsByRun(UUID kbId, UUID runId);

    Optional<PageRevision> findRevision(UUID kbId, UUID pageId, int revision);

    /** Oldest first. */
    List<PageRevision> listRevisions(UUID kbId, UUID pageId);

    List<PageLink> outboundLinks(UUID kbId, Collection<UUID> pageIds);

    List<PageLink> inboundLinks(UUID kbId, Collection<String> paths);

    PageGraph graph(UUID kbId);

    /** Supersessions of these pages whose superseding page is live. */
    List<LiveSupersession> liveSupersessionsOf(UUID kbId, Collection<UUID> pageIds);
}
