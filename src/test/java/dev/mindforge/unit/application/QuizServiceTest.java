package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.mindforge.agent.QuizEvaluator;
import dev.mindforge.agent.QuizGenerator;
import dev.mindforge.application.service.QuizService;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageScore;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.QuizFinishedException;
import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;

class QuizServiceTest {

    private static final UUID KB = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final WikiStore wiki = mock(WikiStore.class);
    private final StudyProgressStore study = mock(StudyProgressStore.class);
    private final QuizSessionStore sessions = mock(QuizSessionStore.class);
    private final StubAIGateway gateway = new StubAIGateway();

    @Test
    void shouldTargetWeakPagesFirstThenUnstudiedByCreationThenTheRest() {
        WikiPage strong = makePage("concepts/strong", 1);
        WikiPage unstudiedLater = makePage("concepts/later", 3);
        WikiPage weak = makePage("concepts/weak", 4);
        WikiPage unstudiedEarlier = makePage("concepts/earlier", 2);
        givenPages(strong, unstudiedLater, weak, unstudiedEarlier);
        when(study.pageScores(KB)).thenReturn(List.of(new PageScore(strong.pageId(), 4.5),
            new PageScore(weak.pageId(), 1.0)));
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/weak"));

        makeService().start(KB, USER, new StudyScope.WholeKnowledgeBase());

        String prompt = gateway.recordedCalls().getFirst().prompt();
        assertThat(List.of("=== concepts/weak", "=== concepts/earlier", "=== concepts/later",
            "=== concepts/strong")).isSortedAccordingTo((a, b) -> Integer.compare(prompt.indexOf(a), prompt.indexOf(b)));
    }

    @Test
    void shouldAskAboutAPageAndItsDirectNeighboursOnly() {
        WikiPage mitosis = makePage("concepts/mitosis", 1);
        WikiPage chromosome = makePage("concepts/chromosome", 2);
        WikiPage dna = makePage("concepts/dna", 3);
        givenPages();
        when(wiki.findById(KB, mitosis.pageId())).thenReturn(Optional.of(mitosis));
        when(wiki.outboundLinks(eq(KB), any())).thenAnswer(call -> Stream.of(
                new PageLink(mitosis.pageId(), chromosome.path(), null), new PageLink(chromosome.pageId(), dna.path(), null))
            .filter(link -> call.<Collection<UUID>>getArgument(1).contains(link.pageId())).toList());
        when(wiki.findByPaths(eq(KB), any())).thenAnswer(call -> Stream.of(mitosis, chromosome, dna)
            .filter(page -> call.<Collection<String>>getArgument(1).contains(page.path())).toList());
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/mitosis"));

        makeService().start(KB, USER, new StudyScope.Page(mitosis.pageId()));

        assertThat(gateway.recordedCalls().getFirst().prompt())
            .contains("=== concepts/mitosis", "=== concepts/chromosome").doesNotContain("=== concepts/dna");
    }

    @Test
    void shouldKeepReferenceAnswersInTheSessionAndHandOnlyTheQuestionOut() {
        WikiPage page = makePage("concepts/mitosis", 1);
        givenPages(page);
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/mitosis").replace("]}", ", {\"pagePath\": \"concepts/made-up\","
            + " \"question\": \"Made up?\", \"referenceAnswer\": \"Yes.\"}]}"));
        QuizService service = makeService();

        QuizSession session = service.start(KB, USER, new StudyScope.WholeKnowledgeBase());
        when(sessions.find(eq(KB), eq(session.sessionId()), any())).thenReturn(Optional.of(session));

        assertThat(session.questions()).singleElement().satisfies(question -> assertThat(question.referenceAnswer())
            .isEqualTo("Cell division."));
        assertThat(service.next(KB, USER, session.sessionId())).contains(new QuizService.NextQuestion(1, 1,
            "What is mitosis?"));
        assertThatExceptionOfType(NotOwnerException.class)
            .isThrownBy(() -> service.next(KB, UUID.randomUUID(), session.sessionId()));
    }

    @Test
    void shouldGradeTheCurrentQuestionRecordItsScoreAndRefuseAnswersOnceFinished() {
        WikiPage page = makePage("concepts/mitosis", 1);
        givenPages(page);
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/mitosis"));
        gateway.answer(Prompts.GRADE, "{\"score\": 9, \"feedback\": \"Dobrze.\"}");
        QuizService service = makeService();
        QuizSession session = service.start(KB, USER, new StudyScope.WholeKnowledgeBase());
        when(sessions.find(eq(KB), eq(session.sessionId()), any())).thenReturn(Optional.of(session),
            Optional.of(session.advanced()));

        QuizService.Graded graded = service.answer(KB, USER, session.sessionId(), "Cell division.");

        assertThat(graded.evaluation().score()).isEqualTo(5);
        assertThat(graded.finished()).isTrue();
        verify(study).recordQuizScore(eq(KB), eq(page.pageId()), eq(5), any());
        verify(sessions).updateCursor(KB, session.sessionId(), 1);
        assertThatExceptionOfType(QuizFinishedException.class)
            .isThrownBy(() -> service.answer(KB, USER, session.sessionId(), "Once more."));
    }

    @Test
    void shouldRefuseWhenTheModelWritesNoUsableQuestion() {
        givenPages(makePage("concepts/mitosis", 1));
        for (String completion : List.of("{\"questions\":[{}]}", "{\"questions\":[null]}", "{\"questions\":[]}",
            "{\"questions\":[{\"pagePath\":null,\"question\":null,\"referenceAnswer\":null}]}", "{}")) {
            gateway.answer(Prompts.QUIZ, completion);

            assertThatExceptionOfType(NotFoundException.class)
                .as(completion)
                .isThrownBy(() -> makeService().start(KB, USER, new StudyScope.WholeKnowledgeBase()));
        }
    }

    @Test
    void shouldAcceptAQuizAtOnceAndWriteItsQuestionsInTheBackground() throws InterruptedException {
        givenPages(makePage("concepts/mitosis", 1));
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/mitosis"));
        QuizService service = makeService();

        QuizSession accepted = service.begin(KB, USER, new StudyScope.WholeKnowledgeBase());

        assertThat(accepted.questions()).as("no question exists when the request returns").isEmpty();
        for (int attempt = 0; attempt < 100 && service.isPreparing(KB, USER, accepted.sessionId()); attempt++) {
            Thread.sleep(20);
        }
        assertThat(service.isPreparing(KB, USER, accepted.sessionId())).isFalse();
        ArgumentCaptor<QuizSession> stored = ArgumentCaptor.forClass(QuizSession.class);
        verify(sessions).insert(eq(KB), stored.capture());
        assertThat(stored.getValue().sessionId()).isEqualTo(accepted.sessionId());
    }

    @Test
    void shouldReportABackgroundFailureOnceAndNotTheNextTime() throws InterruptedException {
        givenPages(makePage("concepts/mitosis", 1));
        gateway.answer(Prompts.QUIZ, "{\"questions\":[]}");
        QuizService service = makeService();
        QuizSession accepted = service.begin(KB, USER, new StudyScope.WholeKnowledgeBase());

        Thread.sleep(300);

        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> service.isPreparing(KB, USER, accepted.sessionId()));
        assertThat(service.isPreparing(KB, USER, accepted.sessionId())).isFalse();
    }

    @Test
    void shouldNotTellAnotherUserWhetherAQuizIsBeingPrepared() {
        givenPages(makePage("concepts/mitosis", 1));
        CountDownLatch release = new CountDownLatch(1);
        gateway.answer(Prompts.QUIZ, prompt -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return questionAbout("concepts/mitosis");
        });
        QuizService service = makeService();
        QuizSession accepted = service.begin(KB, USER, new StudyScope.WholeKnowledgeBase());

        assertThat(service.isPreparing(KB, USER, accepted.sessionId())).isTrue();
        assertThatExceptionOfType(NotOwnerException.class)
            .isThrownBy(() -> service.isPreparing(KB, UUID.randomUUID(), accepted.sessionId()));
        release.countDown();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private QuizService makeService() {
        PromptLoader prompts = new PromptLoader("en");
        return new QuizService(wiki, study, sessions, new QuizGenerator(gateway, prompts),
            new QuizEvaluator(gateway, prompts), ProcessingSettings.defaults(), Duration.ofHours(1));
    }

    private void givenPages(WikiPage... pages) {
        when(wiki.listBodies(KB, PageType.CONCEPT)).thenReturn(List.of(pages));
        when(wiki.liveSupersessionsOf(eq(KB), any())).thenReturn(List.of());
        when(wiki.outboundLinks(eq(KB), any())).thenReturn(List.of());
        when(wiki.findByPaths(eq(KB), any())).thenReturn(List.of());
        when(study.pageScores(KB)).thenReturn(List.of());
    }

    private static String questionAbout(String path) {
        return Prompts.json(Map.of("questions", List.of(Map.of("pagePath", path, "question", "What is mitosis?",
            "referenceAnswer", "Cell division.", "groundingExcerpt", "Mitosis is cell division."))));
    }

    private static WikiPage makePage(String path, int day) {
        Instant created = Instant.parse("2026-09-01T00:00:00Z").plus(day, ChronoUnit.DAYS);
        return new WikiPage(UUID.randomUUID(), KB, path, path, "Description.", PageType.CONCEPT,
            "# Phase\n\nContent " + path + ".\n", 1, created, created);
    }
}
