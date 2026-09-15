package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import dev.mindforge.agent.FlashcardGenerator;
import dev.mindforge.application.service.FlashcardService;
import dev.mindforge.domain.model.CardState;
import dev.mindforge.domain.model.CardType;
import dev.mindforge.domain.model.Flashcard;
import dev.mindforge.domain.model.LiveSupersession;
import dev.mindforge.domain.model.PageScore;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.ProcessingSettings;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.StudyProgressStore;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;

class FlashcardServiceTest {

    private static final UUID KB = UUID.randomUUID();
    private static final String CARDS = "{\"cards\": [{\"type\": \"BASIC\", \"front\": \"Co to mitoza?\","
        + " \"back\": \"Podział komórki.\", \"section\": \"faza\"}]}";

    private final WikiStore wiki = mock(WikiStore.class);
    private final FakeStudyStore study = new FakeStudyStore();
    private final StubAIGateway gateway = new StubAIGateway();

    @Test
    void shouldNotRegenerateAPageWhoseChangeWasOnlyALink() {
        WikiPage before = makePage("concepts/mitoza", "# Faza\n\nMitoza i mejoza.\n", 1);
        WikiPage linked = withBody(before, "# Faza\n\nMitoza i [mejoza](/concepts/mejoza.md).\n");
        givenPages(linked);
        study.cards.add(makeState(before, "stara", Instant.now().minus(1, ChronoUnit.DAYS)));

        makeService(10).deck(KB, new StudyScope.WholeKnowledgeBase());

        assertThat(gateway.recordedCalls()).isEmpty();
    }

    @Test
    void shouldStripSupersededSectionsFromWhatTheGeneratorReads() {
        WikiPage page = makePage("concepts/mitoza", "# Faza\n\nAktualne.\n\n# Stare\n\nNieaktualne twierdzenie.\n", 1);
        givenPages(page);
        when(wiki.liveSupersessionsOf(KB, List.of(page.pageId()))).thenReturn(List.of(new LiveSupersession(
            UUID.randomUUID(), page.pageId(), "stare", UUID.randomUUID(), "concepts/mejoza", "Mejoza")));
        gateway.answer(Prompts.FLASHCARDS, CARDS);

        makeService(10).deck(KB, new StudyScope.WholeKnowledgeBase());

        assertThat(gateway.recordedCalls()).singleElement().satisfies(call ->
            assertThat(call.prompt()).contains("Aktualne.").doesNotContain("Nieaktualne twierdzenie."));
        assertThat(study.cards).extracting(state -> state.card().sectionAnchor()).containsExactly("faza");
    }

    @Test
    void shouldSpendOneBudgetOnStalePagesWithDueCardsFirstThenPagesWithoutCards() {
        WikiPage stale = makePage("concepts/stara", "# A\n\nNowa treść.\n", 3);
        WikiPage newest = makePage("concepts/nowa", "# B\n\nB.\n", 2);
        WikiPage oldest = makePage("concepts/pierwsza", "# C\n\nC.\n", 1);
        givenPages(newest, stale, oldest);
        study.cards.add(makeState(withBody(stale, "# A\n\nStara treść.\n"), "stara", Instant.now().minusSeconds(60)));
        gateway.answer(Prompts.FLASHCARDS, CARDS);

        makeService(2).deck(KB, new StudyScope.WholeKnowledgeBase());

        assertThat(gateway.recordedCalls()).extracting(call -> call.prompt().contains("## Strona: stara")
            ? "stara" : call.prompt().contains("## Strona: pierwsza") ? "pierwsza" : "nowa")
            .containsExactlyInAnyOrder("stara", "pierwsza");
    }

    @Test
    void shouldMakeOneGenerationCallPerPageForTwoConcurrentDeckOpens() throws Exception {
        givenPages(makePage("concepts/mitoza", "# Faza\n\nMitoza.\n", 1));
        CountDownLatch bothOpened = new CountDownLatch(2);
        gateway.answer(Prompts.FLASHCARDS, prompt -> CARDS);
        FlashcardService service = makeService(10);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<CardState>>> decks = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                decks.add(executor.submit(() -> {
                    bothOpened.countDown();
                    return service.deck(KB, new StudyScope.WholeKnowledgeBase());
                }));
            }
            for (Future<List<CardState>> deck : decks) {
                assertThat(deck.get()).hasSize(1);
            }
        }

        assertThat(gateway.recordedCalls()).hasSize(1);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private FlashcardService makeService(int cardPagesPerSession) {
        ProcessingSettings defaults = ProcessingSettings.defaults();
        ProcessingSettings settings = new ProcessingSettings(defaults.chunkSizeTokens(),
            defaults.maxClaimsPerExtractCall(), defaults.maxPageTasksPerRun(), defaults.writerSourceTokens(),
            defaults.supersessionContextTokens(), cardPagesPerSession, Map.of(), Map.of());
        return new FlashcardService(wiki, study, new FlashcardGenerator(gateway, new PromptLoader("pl")), settings);
    }

    private void givenPages(WikiPage... pages) {
        when(wiki.listBodies(KB, PageType.CONCEPT)).thenReturn(List.of(pages));
        for (WikiPage page : pages) {
            when(wiki.findById(KB, page.pageId())).thenReturn(Optional.of(page));
        }
        when(wiki.liveSupersessionsOf(eq(KB), any())).thenReturn(List.of());
    }

    private static WikiPage makePage(String path, String body, int day) {
        Instant created = Instant.parse("2026-09-01T00:00:00Z").plus(day, ChronoUnit.DAYS);
        return new WikiPage(UUID.randomUUID(), KB, path, path.substring(path.indexOf('/') + 1), "Opis.",
            PageType.CONCEPT, body, 1, created, created);
    }

    private static WikiPage withBody(WikiPage page, String body) {
        return new WikiPage(page.pageId(), KB, page.path(), page.title(), page.description(), page.type(), body,
            page.revision() + 1, page.createdAt(), page.updatedAt());
    }

    private static CardState makeState(WikiPage page, String front, Instant due) {
        Flashcard card = new Flashcard(Flashcard.computeCardId(KB, page.pageId(), CardType.BASIC, front, "tył"),
            page.pageId(), null, CardType.BASIC, front, "tył", Flashcard.sourceHash(page.title(), page.markdownBody()));
        return new CardState(card, 2.5, 1, 1, due, null);
    }

    /** Enough of the store to watch a deck: cards by page, replacement and every live card due. */
    private static final class FakeStudyStore implements StudyProgressStore {

        private final List<CardState> cards = new CopyOnWriteArrayList<>();

        @Override
        public List<CardState> cardsOf(UUID kbId, Collection<UUID> pageIds) {
            return cards.stream().filter(state -> pageIds.contains(state.card().pageId())).toList();
        }

        @Override
        public synchronized void replaceCards(UUID kbId, UUID pageId, List<Flashcard> returned, Instant now) {
            cards.removeIf(state -> state.card().pageId().equals(pageId));
            returned.forEach(card -> cards.add(new CardState(card, 2.5, 0, 0, now, null)));
        }

        @Override
        public List<CardState> dueCards(UUID kbId, Collection<UUID> pageIds, Instant now) {
            return cards.stream().filter(state -> !state.retired()).toList();
        }

        @Override
        public Optional<CardState> findCard(UUID kbId, String cardId) {
            return Optional.empty();
        }

        @Override
        public void recordReview(UUID kbId, CardState reviewed, int rating, Instant at) {}

        @Override
        public void recordQuizScore(UUID kbId, UUID pageId, int score, Instant at) {}

        @Override
        public List<PageScore> pageScores(UUID kbId) {
            return List.of();
        }
    }
}
