package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.mindforge.application.service.ArticleFetcher;
import dev.mindforge.application.service.IngestionService;
import dev.mindforge.domain.model.ArticleFetchException;
import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ContentHash;
import dev.mindforge.domain.model.Document;
import dev.mindforge.domain.model.FetchedArticle;
import dev.mindforge.domain.model.LessonAlreadyExistsException;
import dev.mindforge.domain.model.LessonIdentity;
import dev.mindforge.domain.model.LessonIdentityException;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.UploadSource;
import dev.mindforge.domain.port.ArticleSource;
import dev.mindforge.domain.port.DocumentRepository;

class ArticleFetcherTest {

    private static final UUID KB = UUID.randomUUID();
    private static final UUID DOCUMENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final String REMOTE_TEXT = "remote body text";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final ArticleSource articles = mock(ArticleSource.class);
    private final IngestionService ingestion = mock(IngestionService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger fetcherLog = (Logger) LoggerFactory.getLogger(ArticleFetcher.class);

    ArticleFetcherTest() {
        logs.start();
        fetcherLog.addAppender(logs);
        when(articles.fetch(any())).thenAnswer(call -> makeArticle(call.getArgument(0)));
        when(ingestion.ingestArticle(any(), any(), any(), any(), anyString(), anyList()))
            .thenAnswer(call -> new IngestionService.ArticleAccepted(UUID.randomUUID(), false));
    }

    @AfterEach
    void detachAppender() {
        fetcherLog.detachAppender(logs);
    }

    // AC2

    @Test
    void fetchFor_withTheSwitchOff_touchesNothing() {
        givenDocument(UploadSource.API, ContentBlock.text("https://example.org/a", 0));

        makeFetcher(false, 10).fetchFor(KB, DOCUMENT, USER);

        verifyNoInteractions(documents, articles, ingestion);
    }

    // AC3

    @ParameterizedTest
    @EnumSource(value = UploadSource.class, names = {"ARTICLE", "CONVERSATION"})
    void fetchFor_anArticleOrConversationParent_fetchesNothing(UploadSource source) {
        givenDocument(source, ContentBlock.text("https://example.org/a", 0));

        makeFetcher(true, 10).fetchFor(KB, DOCUMENT, USER);

        verifyNoInteractions(articles, ingestion);
    }

    @ParameterizedTest
    @EnumSource(value = UploadSource.class, names = {"API", "CLI", "FILE_WATCHER"})
    void fetchFor_anUploadedParent_fetchesItsUrls(UploadSource source) {
        givenDocument(source, ContentBlock.text("https://example.org/a", 0));

        makeFetcher(true, 10).fetchFor(KB, DOCUMENT, USER);

        verify(articles).fetch(URI.create("https://example.org/a"));
    }

    @Test
    void fetchFor_aMissingDocument_logsAndFetchesNothing() {
        when(documents.findById(KB, DOCUMENT)).thenReturn(Optional.empty());

        makeFetcher(true, 10).fetchFor(KB, DOCUMENT, USER);

        verifyNoInteractions(articles, ingestion);
        assertThat(messages()).anySatisfy(line -> assertThat(line).contains("document not found")
            .contains(DOCUMENT.toString()));
    }

    // AC4

    @Test
    void fetchFor_takesUrlsFromTextAndHeadingBlocksOnly() {
        givenDocument(UploadSource.API,
            ContentBlock.heading("Read https://example.org/heading", 1, 0),
            ContentBlock.code("curl https://example.org/code", 1),
            ContentBlock.text("See [Mitosis](https://example.org/text) for more.", 2));

        assertThat(fetchedUrls(10)).containsExactly("https://example.org/heading", "https://example.org/text");
    }

    @Test
    void fetchFor_dropsFragmentsKeepsQueriesAndDedupsInDocumentOrder() {
        givenDocument(UploadSource.API,
            ContentBlock.text("https://example.org/b#intro and https://example.org/a?lang=en#top", 0),
            ContentBlock.text("[again](https://example.org/b) https://example.org/a?lang=en", 1));

        assertThat(fetchedUrls(10)).containsExactly("https://example.org/b", "https://example.org/a?lang=en");
    }

    @Test
    void fetchFor_trimsTrailingPunctuation() {
        givenDocument(UploadSource.API, ContentBlock.text(
            "(see https://example.org/a). Also https://example.org/b!? and 'https://example.org/c', "
                + "\"https://example.org/d\"; [https://example.org/e]", 0));

        assertThat(fetchedUrls(10)).containsExactly("https://example.org/a", "https://example.org/b",
            "https://example.org/c", "https://example.org/d", "https://example.org/e");
    }

    @Test
    void fetchFor_ignoresOtherSchemesAndUnparsableUrls() {
        givenDocument(UploadSource.API, ContentBlock.text(
            "ftp://example.org/f mailto:someone@example.org https://example.org/%zz https://:80/x "
                + "HTTP://example.org/upper", 0));

        assertThat(fetchedUrls(10)).containsExactly("HTTP://example.org/upper");
    }

    // AC5

    @Test
    void fetchFor_overTheCap_fetchesTheFirstUrlsAndLogsTheSkippedCount() {
        givenDocument(UploadSource.API,
            ContentBlock.text("https://example.org/1 https://example.org/2 https://example.org/3", 0));

        assertThat(fetchedUrls(2)).containsExactly("https://example.org/1", "https://example.org/2");
        assertThat(messages()).contains(
            "Article URLs over cap skipped: documentId=" + DOCUMENT + " skipped=1");
    }

    // AC6

    @Test
    void fetchFor_queuesTheFetchedArticle() {
        givenDocument(UploadSource.CLI, ContentBlock.text("https://example.org/a", 0));
        FetchedArticle article = new FetchedArticle(URI.create("https://example.org/final"), "Mitosis",
            "Mitosis text", List.of(ContentBlock.heading("Mitosis", 1, 0), ContentBlock.text("Mitosis text", 1)));
        when(articles.fetch(URI.create("https://example.org/a"))).thenReturn(article);

        makeFetcher(true, 10).fetchFor(KB, DOCUMENT, USER);

        verify(ingestion).ingestArticle(KB, USER, article.finalUrl(), article.title(), article.text(),
            article.blocks());
    }

    // AC7, AC8

    static Stream<RuntimeException> fetchFailures() {
        return Stream.concat(
            Arrays.stream(ArticleFetchException.Reason.values()).map(ArticleFetchException::new),
            Stream.of(new IllegalStateException(REMOTE_TEXT)));
    }

    @ParameterizedTest
    @MethodSource("fetchFailures")
    void fetchFor_whenAFetchFails_skipsItAndQueuesTheNextUrl(RuntimeException failure) {
        givenDocument(UploadSource.API, ContentBlock.text(
            "https://user:secret@example.org:8443/bad?token=secret#part https://example.org/good", 0));
        when(articles.fetch(URI.create("https://user:secret@example.org:8443/bad?token=secret")))
            .thenThrow(failure);

        makeFetcher(true, 10).fetchFor(KB, DOCUMENT, USER);

        verify(ingestion).ingestArticle(eq(KB), eq(USER), eq(URI.create("https://example.org/good")), any(),
            anyString(), anyList());
        verify(ingestion, never()).ingestArticle(any(), any(),
            eq(URI.create("https://user:secret@example.org:8443/bad?token=secret")), any(), anyString(), anyList());
        assertSkipLogged(reasonOf(failure), "https://example.org:8443/bad");
    }

    static Stream<RuntimeException> ingestFailures() {
        return Stream.of(new LessonAlreadyExistsException(new LessonIdentity("mitosis", "Mitosis")),
            new LessonIdentityException(REMOTE_TEXT), new IllegalArgumentException(REMOTE_TEXT));
    }

    @ParameterizedTest
    @MethodSource("ingestFailures")
    void fetchFor_whenAnIngestFails_skipsItAndQueuesTheNextUrl(RuntimeException failure) {
        givenDocument(UploadSource.API, ContentBlock.text("https://example.org/bad?q=1 https://example.org/good", 0));
        when(ingestion.ingestArticle(any(), any(), eq(URI.create("https://example.org/bad?q=1")), any(), anyString(),
            anyList())).thenThrow(failure);

        makeFetcher(true, 10).fetchFor(KB, DOCUMENT, USER);

        verify(articles).fetch(URI.create("https://example.org/good"));
        verify(ingestion).ingestArticle(eq(KB), eq(USER), eq(URI.create("https://example.org/good")), any(),
            anyString(), anyList());
        assertSkipLogged(reasonOf(failure), "https://example.org/bad");
    }

    @Test
    void fetchFor_aDuplicateArticle_isLoggedWithItsKnowledgeBase() {
        givenDocument(UploadSource.API, ContentBlock.text("https://example.org/same?x=1", 0));
        when(ingestion.ingestArticle(any(), any(), any(), any(), anyString(), anyList()))
            .thenReturn(new IngestionService.ArticleAccepted(UUID.randomUUID(), true));

        makeFetcher(true, 10).fetchFor(KB, DOCUMENT, USER);

        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                .isEqualTo("article skipped reason=DUPLICATE kbId=" + KB + " url=https://example.org/same");
        });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ArticleFetcher makeFetcher(boolean fetchExternalArticles, int maxPerDocument) {
        return new ArticleFetcher(documents, articles, ingestion,
            ProcessingSettings.defaults().withFetchExternalArticles(fetchExternalArticles), maxPerDocument);
    }

    private void givenDocument(UploadSource source, ContentBlock... blocks) {
        when(documents.findById(KB, DOCUMENT)).thenReturn(Optional.of(makeDocument(source, List.of(blocks))));
    }

    private List<String> fetchedUrls(int maxPerDocument) {
        makeFetcher(true, maxPerDocument).fetchFor(KB, DOCUMENT, USER);
        ArgumentCaptor<URI> urls = ArgumentCaptor.forClass(URI.class);
        verify(articles, atLeast(0)).fetch(urls.capture());
        return urls.getAllValues().stream().map(URI::toString).toList();
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertSkipLogged(String reason, String safeUrl) {
        boolean info = reason.equals("LESSON_EXISTS") || reason.equals("DUPLICATE");
        String expected = info
            ? "article skipped reason=" + reason + " kbId=" + KB + " url=" + safeUrl
            : "article skipped reason=" + reason + " url=" + safeUrl;
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(info ? Level.INFO : Level.WARN);
            assertThat(event.getFormattedMessage()).startsWith(expected);
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(messages()).noneSatisfy(line -> assertThat(line).containsAnyOf(REMOTE_TEXT, "secret", "token"));
    }

    private static String reasonOf(RuntimeException failure) {
        return switch (failure) {
            case ArticleFetchException e -> e.reason().name();
            case LessonAlreadyExistsException e -> "LESSON_EXISTS";
            case LessonIdentityException e -> "NO_TITLE";
            default -> "UNEXPECTED";
        };
    }

    private static FetchedArticle makeArticle(URI url) {
        return new FetchedArticle(url, "Title", "Article text", List.of(ContentBlock.text("Article text", 0)));
    }

    private static Document makeDocument(UploadSource source, List<ContentBlock> blocks) {
        Instant now = Instant.parse("2026-11-01T00:00:00Z");
        return new Document(DOCUMENT, KB, new LessonIdentity("cells", "Cells"), ContentHash.compute(new byte[] {1}),
            "cells.md", "text/markdown", "", blocks, source, USER, now, now);
    }
}
