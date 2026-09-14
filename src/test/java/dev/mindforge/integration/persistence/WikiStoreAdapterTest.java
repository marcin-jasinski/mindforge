package dev.mindforge.integration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.IngestRun;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.PageGraph;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageRevision;
import dev.mindforge.domain.model.PageSource;
import dev.mindforge.domain.model.PageSupersession;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.PageWrite;
import dev.mindforge.domain.model.RunKind;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.IngestRunRepository;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.support.TestContainerBase;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "mindforge.security.jwt-secret=test-jwt-secret-for-integration-tests-minimum-length",
    "spring.ai.openai.api-key=test-placeholder",
    "spring.jpa.hibernate.ddl-auto=none"
})
class WikiStoreAdapterTest extends TestContainerBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WikiStore wiki;

    @Autowired
    private IngestRunRepository runs;

    @Test
    void twoPagesCannotShareAPathInOneKnowledgeBase() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID otherKbId = insertKnowledgeBase(userId);
        wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/mitoza", "Treść.\n"), enqueueRun(kbId));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
            wiki.savePage(kbId, makeWrite(UUID.randomUUID(), "concepts/mitoza", "Inna.\n"), enqueueRun(kbId)));

        wiki.savePage(otherKbId, makeWrite(UUID.randomUUID(), "concepts/mitoza", "Treść.\n"), enqueueRun(otherKbId));
        assertThat(wiki.findByPath(otherKbId, "concepts/mitoza")).isPresent();
    }

    @Test
    void aLinkRowCannotBelongToAnotherKnowledgeBasesPage() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID otherKbId = insertKnowledgeBase(userId);
        UUID pageId = UUID.randomUUID();
        wiki.savePage(kbId, makeWrite(pageId, "concepts/mitoza", "Treść.\n"), enqueueRun(kbId));

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> jdbc.update(
            "INSERT INTO page_links (knowledge_base_id, source_page_id, target_path) VALUES (?, ?, 'concepts/x')",
            otherKbId, pageId));
    }

    @Test
    void savingABodyReDerivesItsDistinctInternalLinks() {
        UUID kbId = insertKnowledgeBase(insertUser());
        UUID runId = enqueueRun(kbId);
        UUID p = UUID.randomUUID();
        UUID q = UUID.randomUUID();
        String body = "# Faza\nZob. [Q](/concepts/q.md#faza), [znów Q](/concepts/q.md#faza), [lekcja](/sources/bio-1.md)"
            + " i [PubMed](https://pubmed.ncbi.nlm.nih.gov/1).\n```\n[X](/concepts/x.md)\n```\n";

        wiki.savePage(kbId, makeWrite(p, "concepts/p", body), runId);
        wiki.savePage(kbId, makeWrite(q, "concepts/q", "# Faza\n"), runId);

        assertThat(wiki.outboundLinks(kbId, List.of(p))).containsExactlyInAnyOrder(
            new PageLink(p, "concepts/q", "faza"), new PageLink(p, "sources/bio-1", null));
        assertThat(wiki.inboundLinks(kbId, List.of("concepts/q"))).containsExactly(new PageLink(p, "concepts/q", "faza"));
        PageGraph graph = wiki.graph(kbId);
        assertThat(graph.pages()).extracting(IndexEntry::path).containsExactlyInAnyOrder("concepts/p", "concepts/q");
        assertThat(graph.links()).containsExactlyInAnyOrder(
            new PageGraph.Edge("concepts/p", "concepts/q"), new PageGraph.Edge("concepts/p", "sources/bio-1"));

        wiki.savePage(kbId, makeWrite(p, "concepts/p", "# Faza\nBez linków.\n"), enqueueRun(kbId));

        assertThat(wiki.outboundLinks(kbId, List.of(p))).isEmpty();
        assertThat(wiki.findById(kbId, p)).map(WikiPage::revision).contains(2);
        assertThat(wiki.listRevisions(kbId, p)).extracting(PageRevision::revision).containsExactly(1, 2);
    }

    @Test
    void deletingAPageTombstonesItAndDropsItsLinksWhileItsHistoryStays() {
        UUID kbId = insertKnowledgeBase(insertUser());
        UUID pageId = UUID.randomUUID();
        wiki.savePage(kbId, makeWrite(pageId, "concepts/p", "[Q](/concepts/q.md)\n"), enqueueRun(kbId));
        UUID deletingRun = enqueueRun(kbId);

        PageRevision tombstone = wiki.deletePage(kbId, pageId, deletingRun);

        assertThat(wiki.findById(kbId, pageId)).isEmpty();
        assertThat(wiki.outboundLinks(kbId, List.of(pageId))).isEmpty();
        assertThat(wiki.listRevisions(kbId, pageId)).extracting(PageRevision::isTombstone).containsExactly(false, true);
        assertThat(wiki.tipRevisions(kbId, List.of(pageId))).containsEntry(pageId, tombstone);
        assertThat(wiki.revisionsByRun(kbId, deletingRun)).containsExactly(tombstone);
        assertThat(wiki.tipRevisions(kbId, List.of())).isEmpty();
    }

    @Test
    void aSupersessionIsLiveOnlyWhileBothItsPagesAre() {
        UUID kbId = insertKnowledgeBase(insertUser());
        UUID runId = enqueueRun(kbId);
        UUID superseded = UUID.randomUUID();
        UUID superseding = UUID.randomUUID();
        UUID supersessionId = UUID.randomUUID();
        wiki.savePage(kbId, makeWrite(superseded, "concepts/p", "# Faza\n"), runId);
        wiki.savePage(kbId, makeWrite(superseding, "concepts/q", "# Faza\n"), runId);
        wiki.addSupersessions(kbId, List.of(
            new PageSupersession(supersessionId, superseded, "faza", superseding, runId, Instant.now())));

        assertThat(wiki.liveSupersessionsOf(kbId, List.of(superseded))).containsExactly(new LiveSupersession(
            supersessionId, superseded, "faza", superseding, "concepts/q", "Tytuł concepts/q"));

        wiki.deletePage(kbId, superseding, enqueueRun(kbId));

        assertThat(wiki.liveSupersessionsOf(kbId, List.of(superseded))).isEmpty();
    }

    @Test
    void theLessonScopeListsLiveConceptsWithASourceFromThatLesson() {
        UUID userId = insertUser();
        UUID kbId = insertKnowledgeBase(userId);
        UUID lesson1 = insertDocument(kbId, userId, "bio-1");
        UUID lesson2 = insertDocument(kbId, userId, "bio-2");
        UUID runId = enqueueRun(kbId);
        UUID p = UUID.randomUUID();
        UUID q = UUID.randomUUID();
        UUID summary = UUID.randomUUID();
        wiki.savePage(kbId, makeWrite(p, "concepts/p", "P.\n"), runId);
        wiki.savePage(kbId, makeWrite(q, "concepts/q", "Q.\n"), runId);
        wiki.savePage(kbId, new PageWrite(summary, "sources/bio-1", "Lekcja 1", "Opis.", PageType.SOURCE_SUMMARY,
            "Streszczenie.\n"), runId);
        wiki.addSources(kbId, List.of(new PageSource(p, lesson1, runId), new PageSource(q, lesson2, runId),
            new PageSource(summary, lesson1, runId)));

        assertThat(wiki.pageIdsForLesson(kbId, "bio-1")).containsExactly(p);
        assertThat(wiki.listIndex(kbId)).extracting(IndexEntry::path, IndexEntry::type).containsExactlyInAnyOrder(
            tuple("concepts/p", PageType.CONCEPT),
            tuple("concepts/q", PageType.CONCEPT),
            tuple("sources/bio-1", PageType.SOURCE_SUMMARY));
        assertThat(wiki.listBodies(kbId, PageType.CONCEPT)).extracting(WikiPage::path)
            .containsExactlyInAnyOrder("concepts/p", "concepts/q");
        assertThat(wiki.findByPaths(kbId, List.of("concepts/p", "sources/bio-1", "concepts/x")))
            .extracting(WikiPage::pageId).containsExactlyInAnyOrder(p, summary);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private UUID enqueueRun(UUID kbId) {
        return runs.enqueue(kbId, IngestRun.queued(kbId, RunKind.INGEST, null, null)).runId();
    }

    private static PageWrite makeWrite(UUID pageId, String path, String body) {
        return new PageWrite(pageId, path, "Tytuł " + path, "Opis.", PageType.CONCEPT, body);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, display_name, email) VALUES (?, 'Test User', ?)",
            userId, "wiki-" + userId + "@example.com");
        return userId;
    }

    private UUID insertKnowledgeBase(UUID ownerId) {
        UUID kbId = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_bases (kb_id, owner_id, name) VALUES (?, ?, 'Test KB')", kbId, ownerId);
        return kbId;
    }

    private UUID insertDocument(UUID kbId, UUID userId, String lessonId) {
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (document_id, knowledge_base_id, lesson_id, lesson_title, content_hash,"
                + " source_filename, mime_type, upload_source, uploaded_by)"
                + " VALUES (?, ?, ?, ?, ?, 'notatki.md', 'text/markdown', 'API', ?)",
            documentId, kbId, lessonId, "Lekcja " + lessonId, documentId.toString(), userId);
        return documentId;
    }
}
