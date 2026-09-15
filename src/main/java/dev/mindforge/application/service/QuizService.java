package dev.mindforge.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.mindforge.agent.QuizEvaluator;
import dev.mindforge.agent.QuizGenerator;
import dev.mindforge.application.study.StudyPages;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageScore;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.QuizEvaluation;
import dev.mindforge.domain.model.QuizFinishedException;
import dev.mindforge.domain.model.QuizQuestion;
import dev.mindforge.domain.model.QuizSession;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.TokenEstimate;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;

/**
 * Server-authoritative quizzes (T08, T27): one generation call per session over the scope's pages — weak pages first,
 * then unstudied Concepts by creation, then the rest by ascending mean, then their linked neighbours — graded against
 * reference answers that never leave the session row.
 */
public class QuizService {

    private static final int QUESTIONS_PER_SESSION = 5;

    /** A question as the learner sees it: its text and place in the quiz, nothing else. */
    public record NextQuestion(int index, int total, String question) {}

    /** A graded answer and whether the quiz has more questions. */
    public record Graded(QuizEvaluation evaluation, boolean finished) {}

    private final WikiStore wiki;
    private final StudyProgressStore study;
    private final QuizSessionStore sessions;
    private final QuizGenerator generator;
    private final QuizEvaluator evaluator;
    private final ProcessingSettings settings;
    private final Duration sessionTtl;

    public QuizService(WikiStore wiki, StudyProgressStore study, QuizSessionStore sessions, QuizGenerator generator,
                       QuizEvaluator evaluator, ProcessingSettings settings, Duration sessionTtl) {
        this.wiki = wiki;
        this.study = study;
        this.sessions = sessions;
        this.generator = generator;
        this.evaluator = evaluator;
        this.settings = settings;
        this.sessionTtl = sessionTtl;
    }

    /** @throws NotFoundException when the scope has no page to ask about, or the model wrote no usable question */
    public QuizSession start(UUID kbId, UUID userId, StudyScope scope) {
        List<WikiPage> ordered = targetOrder(kbId, StudyPages.of(wiki, kbId, scope));
        Map<String, WikiPage> shown = new LinkedHashMap<>();
        int tokens = 0;
        for (WikiPage page : withNeighbours(kbId, ordered)) {
            String body = StudyPages.unsupersededBody(wiki, kbId, page);
            if (!shown.isEmpty() && tokens + TokenEstimate.of(body) > settings.chunkSizeTokens()) {
                break;
            }
            tokens += TokenEstimate.of(body);
            shown.put(page.path(), new WikiPage(page.pageId(), page.knowledgeBaseId(), page.path(), page.title(),
                page.description(), page.type(), body, page.revision(), page.createdAt(), page.updatedAt()));
        }
        if (shown.isEmpty()) {
            throw new NotFoundException("Pages to study");
        }
        Map<String, String> bodies = new LinkedHashMap<>();
        shown.forEach((path, page) -> bodies.put(path, page.markdownBody()));
        List<QuizQuestion> questions = generator.generate(bodies, QUESTIONS_PER_SESSION).stream()
            .filter(draft -> draft.pagePath() != null && shown.containsKey(draft.pagePath()) && notBlank(draft.question())
                && notBlank(draft.referenceAnswer()))
            .limit(QUESTIONS_PER_SESSION)
            .map(draft -> new QuizQuestion(shown.get(draft.pagePath()).pageId(), draft.sectionAnchor(), draft.question(),
                draft.referenceAnswer(), draft.groundingExcerpt()))
            .toList();
        if (questions.isEmpty()) {
            throw new NotFoundException("Questions for this scope");
        }
        QuizSession session = new QuizSession(UUID.randomUUID(), kbId, userId, questions, 0,
            Instant.now().plus(sessionTtl));
        sessions.insert(kbId, session);
        return session;
    }

    public Optional<NextQuestion> next(UUID kbId, UUID userId, UUID sessionId) {
        QuizSession session = owned(kbId, userId, sessionId);
        return session.finished()
            ? Optional.empty()
            : Optional.of(new NextQuestion(session.cursor() + 1, session.questions().size(),
                session.questions().get(session.cursor()).question()));
    }

    /** Grades the current question, records the score on its page and moves on. */
    public Graded answer(UUID kbId, UUID userId, UUID sessionId, String answer) {
        QuizSession session = owned(kbId, userId, sessionId);
        if (session.finished()) {
            throw new QuizFinishedException(sessionId);
        }
        QuizQuestion question = session.questions().get(session.cursor());
        QuizEvaluation evaluation = evaluator.grade(question.question(), question.referenceAnswer(),
            question.groundingExcerpt(), answer);
        // ponytail: two answers racing on one question both grade; a conditional cursor update fixes it if it matters
        study.recordQuizScore(kbId, question.pageId(), evaluation.score(), Instant.now());
        QuizSession advanced = session.advanced();
        sessions.updateCursor(kbId, sessionId, advanced.cursor());
        return new Graded(evaluation, advanced.finished());
    }

    /** Weak pages by ascending mean, then unstudied pages by creation, then the rest by ascending mean. */
    private List<WikiPage> targetOrder(UUID kbId, List<WikiPage> pages) {
        Map<UUID, Double> means = study.pageScores(kbId).stream()
            .collect(Collectors.toMap(PageScore::pageId, PageScore::mean));
        Comparator<WikiPage> byMean = Comparator.comparing(page -> means.get(page.pageId()));
        List<WikiPage> ordered = new ArrayList<>();
        pages.stream().filter(page -> means.containsKey(page.pageId()) && means.get(page.pageId()) < PageScore.WEAK_BELOW)
            .sorted(byMean).forEach(ordered::add);
        pages.stream().filter(page -> !means.containsKey(page.pageId()))
            .sorted(Comparator.comparing(WikiPage::createdAt)).forEach(ordered::add);
        pages.stream().filter(page -> means.containsKey(page.pageId()) && means.get(page.pageId()) >= PageScore.WEAK_BELOW)
            .sorted(byMean).forEach(ordered::add);
        return ordered;
    }

    private List<WikiPage> withNeighbours(UUID kbId, List<WikiPage> ordered) {
        Set<String> paths = ordered.stream().map(WikiPage::path).collect(Collectors.toSet());
        List<String> linked = wiki.outboundLinks(kbId, ordered.stream().map(WikiPage::pageId).toList()).stream()
            .map(PageLink::targetPath).filter(path -> !paths.contains(path)).distinct().toList();
        List<WikiPage> all = new ArrayList<>(ordered);
        wiki.findByPaths(kbId, linked).stream().filter(page -> page.type().equals(PageType.CONCEPT)).forEach(all::add);
        return all;
    }

    private QuizSession owned(UUID kbId, UUID userId, UUID sessionId) {
        QuizSession session = sessions.find(kbId, sessionId, Instant.now())
            .orElseThrow(() -> new NotFoundException("Quiz session"));
        if (!session.userId().equals(userId)) {
            throw new NotOwnerException(kbId);
        }
        return session;
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }
}
