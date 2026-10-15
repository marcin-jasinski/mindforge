package dev.mindforge.application.service;

import java.util.List;
import java.util.UUID;

import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.application.wiki.PageRenderer;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.PageGraph;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.BundleQuery;
import dev.mindforge.domain.port.WikiStore;

/** The read side of the wiki the SPA browses: the index, a rendered page, its history and the link graph. */
public class WikiService {

    public record IndexView(String markdown, List<IndexEntry> pages) {}

    /** A live page and its rendering, with supersession notes and citations. */
    public record PageView(WikiPage page, String markdown) {}

    private final WikiStore wiki;
    private final BundleQuery bundle;

    public WikiService(WikiStore wiki, BundleQuery bundle) {
        this.wiki = wiki;
        this.bundle = bundle;
    }

    public IndexView index(UUID kbId) {
        List<IndexEntry> pages = wiki.listIndex(kbId);
        return new IndexView(IndexRenderer.render(pages), pages);
    }

    /** @throws NotFoundException when no live page has the path */
    public PageView page(UUID kbId, String path) {
        WikiPage page = live(kbId, path);
        List<UUID> id = List.of(page.pageId());
        return new PageView(page, PageRenderer.render(page, wiki.liveSupersessionsOf(kbId, id),
            bundle.citations(kbId, id)));
    }

    /** Oldest first, so each revision's prior revision is the one before it. */
    public List<PageRevision> revisions(UUID kbId, String path) {
        return wiki.listRevisions(kbId, live(kbId, path).pageId());
    }

    public PageGraph graph(UUID kbId) {
        return wiki.graph(kbId);
    }

    private WikiPage live(UUID kbId, String path) {
        return wiki.findByPath(kbId, path).orElseThrow(() -> new NotFoundException("Page " + path));
    }
}
