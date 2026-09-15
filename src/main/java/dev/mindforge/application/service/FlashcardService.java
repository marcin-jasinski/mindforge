package dev.mindforge.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.agent.FlashcardGenerator;
import dev.mindforge.application.study.SM2Scheduler;
import dev.mindforge.application.study.StudyPages;
import dev.mindforge.domain.model.CardDraft;
import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.MarkdownStructure;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.ReviewResult;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;

/**
 * Flashcards cut lazily from Concept pages (ADR 0018, T21, T27). Opening a deck spends at most
 * {@code cardPagesPerSession} generation calls — stale pages with due cards first, oldest due first, then pages
 * without cards by creation — in parallel, and waits for them. A page is stale when its content hash differs from its
 * cards'; stale pages past the budget keep serving their cards.
 */
public class FlashcardService {

    private static final Logger log = LoggerFactory.getLogger(FlashcardService.class);

    private final WikiStore wiki;
    private final StudyProgressStore study;
    private final FlashcardGenerator generator;
    private final ProcessingSettings settings;
    private final Map<UUID, ReentrantLock> pageLocks = new ConcurrentHashMap<>();

    public FlashcardService(WikiStore wiki, StudyProgressStore study, FlashcardGenerator generator,
                            ProcessingSettings settings) {
        this.wiki = wiki;
        this.study = study;
        this.generator = generator;
        this.settings = settings;
    }

    /** Refreshes the scope's cards within the budget, then returns its due cards, oldest due first. */
    public List<CardState> deck(UUID kbId, StudyScope scope) {
        Instant now = Instant.now();
        List<WikiPage> pages = StudyPages.of(wiki, kbId, scope);
        List<UUID> ids = pages.stream().map(WikiPage::pageId).toList();
        Map<UUID, List<CardState>> live = study.cardsOf(kbId, ids).stream()
            .filter(card -> !card.retired())
            .collect(Collectors.groupingBy(card -> card.card().pageId()));

        Map<UUID, Instant> staleDue = new LinkedHashMap<>();
        List<WikiPage> uncarded = new ArrayList<>();
        for (WikiPage page : pages) {
            List<CardState> cards = live.getOrDefault(page.pageId(), List.of());
            String hash = Flashcard.sourceHash(page.title(), page.markdownBody());
            if (cards.isEmpty()) {
                uncarded.add(page);
            } else if (cards.stream().anyMatch(card -> !card.card().sourceHash().equals(hash))) {
                cards.stream().map(CardState::dueAt).filter(due -> !due.isAfter(now)).min(Comparator.naturalOrder())
                    .ifPresent(due -> staleDue.put(page.pageId(), due));
            }
        }
        Map<UUID, WikiPage> byId = pages.stream().collect(Collectors.toMap(WikiPage::pageId, page -> page));
        List<WikiPage> budget = new ArrayList<>(staleDue.entrySet().stream()
            .sorted(Map.Entry.comparingByValue()).map(entry -> byId.get(entry.getKey())).toList());
        uncarded.stream().sorted(Comparator.comparing(WikiPage::createdAt)).forEach(budget::add);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            budget.stream().limit(settings.cardPagesPerSession())
                .forEach(page -> executor.submit(() -> regenerate(kbId, page.pageId(), now)));
        }
        return study.dueCards(kbId, scope instanceof StudyScope.WholeKnowledgeBase ? null : ids, now);
    }

    /** Rates a card, reschedules it by SM-2 and records the rating as a study event. */
    public CardState review(UUID kbId, String cardId, ReviewResult result) {
        Instant now = Instant.now();
        CardState card = study.findCard(kbId, cardId).filter(found -> !found.retired())
            .orElseThrow(() -> new NotFoundException("Flashcard"));
        CardState reviewed = SM2Scheduler.review(card, result, now);
        study.recordReview(kbId, reviewed, result.rating(), now);
        return reviewed;
    }

    /** Generates one page's cards under its lock, after checking again that they are still missing or stale. */
    private void regenerate(UUID kbId, UUID pageId, Instant now) {
        ReentrantLock lock = pageLocks.computeIfAbsent(pageId, id -> new ReentrantLock());
        lock.lock();
        try {
            WikiPage page = wiki.findById(kbId, pageId).orElse(null);
            if (page == null) {
                return;
            }
            String hash = Flashcard.sourceHash(page.title(), page.markdownBody());
            List<CardState> cards = study.cardsOf(kbId, List.of(pageId));
            List<Flashcard> current = cards.stream().filter(card -> !card.retired()).map(CardState::card).toList();
            if (!current.isEmpty() && current.stream().allMatch(card -> card.sourceHash().equals(hash))) {
                return;
            }
            List<Flashcard> reusable = cards.stream().filter(CardState::retired).map(CardState::card)
                .filter(card -> card.sourceHash().equals(hash)).toList();
            String body = StudyPages.unsupersededBody(wiki, kbId, page);
            Set<String> anchors = MarkdownStructure.sections(body).stream().map(MarkdownStructure.Section::anchor)
                .collect(Collectors.toSet());
            List<CardDraft> drafts = generator.generate(page.title(), body, anchors, current, reusable);
            List<Flashcard> returned = drafts.stream()
                .map(draft -> new Flashcard(Flashcard.computeCardId(kbId, pageId, draft.cardType(), draft.front(),
                    draft.back()), pageId, draft.sectionAnchor(), draft.cardType(), draft.front(), draft.back(), hash))
                .filter(distinctBy(Flashcard::cardId))
                .toList();
            study.replaceCards(kbId, pageId, returned, now);
        } catch (RuntimeException e) {
            log.warn("Could not generate flashcards for page {}; its cards keep serving", pageId, e);
        } finally {
            lock.unlock();
            // ponytail: a waiter can race this removal into a second lock; the re-check and conflict-ignoring inserts keep that harmless
            if (!lock.hasQueuedThreads()) {
                pageLocks.remove(pageId, lock);
            }
        }
    }

    private static <T> Predicate<T> distinctBy(Function<T, Object> key) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return value -> seen.add(Objects.requireNonNull(key.apply(value)));
    }
}
