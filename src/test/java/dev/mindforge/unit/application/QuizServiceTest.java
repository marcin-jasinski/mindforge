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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.mindforge.agent.QuizEvaluator;
import dev.mindforge.agent.QuizGenerator;
import dev.mindforge.application.service.QuizService;
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
        WikiPage strong = makePage("concepts/mocna", 1);
        WikiPage unstudiedLater = makePage("concepts/pozniejsza", 3);
        WikiPage weak = makePage("concepts/slaba", 4);
        WikiPage unstudiedEarlier = makePage("concepts/wczesniejsza", 2);
        givenPages(strong, unstudiedLater, weak, unstudiedEarlier);
        when(study.pageScores(KB)).thenReturn(List.of(new PageScore(strong.pageId(), 4.5),
            new PageScore(weak.pageId(), 1.0)));
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/slaba"));

        makeService().start(KB, USER, new StudyScope.WholeKnowledgeBase());

        String prompt = gateway.recordedCalls().getFirst().prompt();
        assertThat(List.of("=== concepts/slaba", "=== concepts/wczesniejsza", "=== concepts/pozniejsza",
            "=== concepts/mocna")).isSortedAccordingTo((a, b) -> Integer.compare(prompt.indexOf(a), prompt.indexOf(b)));
    }

    @Test
    void shouldAskAboutAPageAndItsDirectNeighboursOnly() {
        WikiPage mitoza = makePage("concepts/mitoza", 1);
        WikiPage chromosom = makePage("concepts/chromosom", 2);
        WikiPage dna = makePage("concepts/dna", 3);
        givenPages();
        when(wiki.findById(KB, mitoza.pageId())).thenReturn(Optional.of(mitoza));
        when(wiki.outboundLinks(eq(KB), any())).thenAnswer(call -> Stream.of(
                new PageLink(mitoza.pageId(), chromosom.path(), null), new PageLink(chromosom.pageId(), dna.path(), null))
            .filter(link -> call.<Collection<UUID>>getArgument(1).contains(link.pageId())).toList());
        when(wiki.findByPaths(eq(KB), any())).thenAnswer(call -> Stream.of(mitoza, chromosom, dna)
            .filter(page -> call.<Collection<String>>getArgument(1).contains(page.path())).toList());
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/mitoza"));

        makeService().start(KB, USER, new StudyScope.Page(mitoza.pageId()));

        assertThat(gateway.recordedCalls().getFirst().prompt())
            .contains("=== concepts/mitoza", "=== concepts/chromosom").doesNotContain("=== concepts/dna");
    }

    @Test
    void shouldKeepReferenceAnswersInTheSessionAndHandOnlyTheQuestionOut() {
        WikiPage page = makePage("concepts/mitoza", 1);
        givenPages(page);
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/mitoza").replace("]}", ", {\"pagePath\": \"concepts/zmyslona\","
            + " \"question\": \"Zmyślone?\", \"referenceAnswer\": \"Tak.\"}]}"));
        QuizService service = makeService();

        QuizSession session = service.start(KB, USER, new StudyScope.WholeKnowledgeBase());
        when(sessions.find(eq(KB), eq(session.sessionId()), any())).thenReturn(Optional.of(session));

        assertThat(session.questions()).singleElement().satisfies(question -> assertThat(question.referenceAnswer())
            .isEqualTo("Podział komórki."));
        assertThat(service.next(KB, USER, session.sessionId())).contains(new QuizService.NextQuestion(1, 1,
            "Co to mitoza?"));
        assertThatExceptionOfType(NotOwnerException.class)
            .isThrownBy(() -> service.next(KB, UUID.randomUUID(), session.sessionId()));
    }

    @Test
    void shouldGradeTheCurrentQuestionRecordItsScoreAndRefuseAnswersOnceFinished() {
        WikiPage page = makePage("concepts/mitoza", 1);
        givenPages(page);
        gateway.answer(Prompts.QUIZ, questionAbout("concepts/mitoza"));
        gateway.answer(Prompts.GRADE, "{\"score\": 9, \"feedback\": \"Dobrze.\"}");
        QuizService service = makeService();
        QuizSession session = service.start(KB, USER, new StudyScope.WholeKnowledgeBase());
        when(sessions.find(eq(KB), eq(session.sessionId()), any())).thenReturn(Optional.of(session),
            Optional.of(session.advanced()));

        QuizService.Graded graded = service.answer(KB, USER, session.sessionId(), "Podział komórki.");

        assertThat(graded.evaluation().score()).isEqualTo(5);
        assertThat(graded.finished()).isTrue();
        verify(study).recordQuizScore(eq(KB), eq(page.pageId()), eq(5), any());
        verify(sessions).updateCursor(KB, session.sessionId(), 1);
        assertThatExceptionOfType(QuizFinishedException.class)
            .isThrownBy(() -> service.answer(KB, USER, session.sessionId(), "Jeszcze raz."));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private QuizService makeService() {
        PromptLoader prompts = new PromptLoader("pl");
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
        return Prompts.json(Map.of("questions", List.of(Map.of("pagePath", path, "question", "Co to mitoza?",
            "referenceAnswer", "Podział komórki.", "groundingExcerpt", "Mitoza to podział komórki."))));
    }

    private static WikiPage makePage(String path, int day) {
        Instant created = Instant.parse("2026-09-01T00:00:00Z").plus(day, ChronoUnit.DAYS);
        return new WikiPage(UUID.randomUUID(), KB, path, path, "Opis.", PageType.CONCEPT,
            "# Faza\n\nTreść " + path + ".\n", 1, created, created);
    }
}
