package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.mindforge.agent.AnswerWriter;
import dev.mindforge.agent.PageSelector;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.application.wiki.IndexRenderer;
import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.InteractionTurn;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.TokenEstimate;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.InteractionStore;
import dev.mindforge.domain.port.PageSearchQuery;
import dev.mindforge.domain.port.WikiStore;
import dev.mindforge.infrastructure.ai.PromptLoader;
import dev.mindforge.support.Prompts;
import dev.mindforge.support.StubAIGateway;

class QueryServiceTest {

    private static final UUID KB = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    private final WikiStore wiki = mock(WikiStore.class);
    private final InteractionStore interactions = mock(InteractionStore.class);
    private final StubAIGateway gateway = new StubAIGateway();
    private final PageSearchQuery search = mock(PageSearchQuery.class);

    @Test
    void shouldDropHallucinatedPathsAddLinkedNeighboursAndCiteOnlyPagesItWasGiven() {
        WikiPage mitosis = makePage("concepts/mitosis", "Mitosis splits a cell.");
        WikiPage meiosis = makePage("concepts/meiosis", "Meiosis makes gametes.");
        givenSession();
        when(wiki.findByPaths(KB, List.of("concepts/mitosis", "concepts/made-up"))).thenReturn(List.of(mitosis));
        when(wiki.outboundLinks(KB, List.of(mitosis.pageId())))
            .thenReturn(List.of(new PageLink(mitosis.pageId(), "concepts/meiosis", null)));
        when(wiki.findByPaths(KB, List.of("concepts/meiosis"))).thenReturn(List.of(meiosis));
        gateway.answer(Prompts.SELECT, "{\"paths\": [\"concepts/mitosis\", \"concepts/made-up\"]}");
        gateway.answer(Prompts.ANSWER, "{\"answer\": \"Mitosis splits a cell.\","
            + " \"citations\": [\"concepts/mitosis\", \"concepts/made-up\"]}");

        QueryService.Answer answer = makeService().ask(KB, USER, SESSION, "What is mitosis?");

        assertThat(answer.citedPaths()).containsExactly("concepts/mitosis");
        assertThat(gateway.recordedCalls().getLast().prompt()).contains("Mitosis splits a cell.")
            .contains("Meiosis makes gametes.");
        ArgumentCaptor<InteractionTurn> turn = ArgumentCaptor.forClass(InteractionTurn.class);
        verify(interactions).addTurn(eq(KB), eq(SESSION), turn.capture());
        assertThat(turn.getValue().usedPagePaths()).containsExactly("concepts/mitosis", "concepts/meiosis");
    }

    @Test
    void shouldStopAddingContextAtTheFirstPageThatDoesNotFitTheBudget() {
        WikiPage small = makePage("concepts/mitosis", "A small page.");
        WikiPage huge = makePage("concepts/big", "x".repeat(90_000));
        WikiPage after = makePage("concepts/meiosis", "The page after the big one.");
        givenSession();
        when(wiki.findByPaths(eq(KB), any())).thenReturn(List.of(small, huge, after));
        when(wiki.outboundLinks(eq(KB), any())).thenReturn(List.of());
        gateway.answer(Prompts.SELECT, "{\"paths\": [\"concepts/mitosis\", \"concepts/big\", \"concepts/meiosis\"]}");
        gateway.answer(Prompts.ANSWER, "{\"answer\": \"Answer.\", \"citations\": []}");

        makeService().ask(KB, USER, SESSION, "Question?");

        assertThat(gateway.recordedCalls().getLast().prompt()).contains("A small page.").doesNotContain("xxxxxxxxxx")
            .doesNotContain("The page after the big one.");
    }

    @Test
    void shouldRefuseAnotherUsersConversation() {
        givenSession();

        assertThatExceptionOfType(NotOwnerException.class)
            .isThrownBy(() -> makeService().ask(KB, UUID.randomUUID(), SESSION, "Question?"));
    }

    @Test
    void shouldRouteOverTheLexicalShortlistWhenTheIndexIsPastTheCeiling() {
        givenSession();
        givenAnOversizedIndex();
        IndexEntry hit = makeEntry("concepts/mitosis", "Mitosis", "Cell division.");
        when(search.search(eq(KB), any(), eq(20))).thenReturn(List.of(hit));
        gateway.answer(Prompts.SELECT, "{\"paths\": []}");
        gateway.answer(Prompts.ANSWER, "{\"answer\": \"Answer.\", \"citations\": []}");

        makeService().ask(KB, USER, SESSION, "What does mitosis mean?");

        String select = gateway.recordedCalls().getFirst().prompt();
        assertThat(select).contains("concepts/mitosis").doesNotContain("concepts/other-500");
    }

    @Test
    void shouldBoundTheIndexInThePromptWhenNothingMatchesAndTheIndexIsPastTheCeiling() {
        givenSession();
        givenAnOversizedIndex();
        when(search.search(eq(KB), any(), eq(20))).thenReturn(List.of());
        gateway.answer(Prompts.SELECT, "{\"paths\": []}");
        gateway.answer(Prompts.ANSWER, "{\"answer\": \"Answer.\", \"citations\": []}");

        makeService().ask(KB, USER, SESSION, "What does mitosis mean?");

        // the whole index is ~45K tokens; the prompt carries at most the ceiling plus the prompt template
        assertThat(TokenEstimate.of(gateway.recordedCalls().getFirst().prompt()))
            .isLessThanOrEqualTo(IndexRenderer.RETRIEVAL_CEILING_TOKENS + 2_000);
    }

    private void givenAnOversizedIndex() {
        List<IndexEntry> entries = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            entries.add(makeEntry("concepts/other-" + i, "Other concept " + i, "Description. ".repeat(20)));
        }
        when(wiki.listIndex(KB)).thenReturn(entries);
    }

    private QueryService makeService() {
        PromptLoader prompts = new PromptLoader("en");
        return new QueryService(wiki, interactions, new PageSelector(gateway, prompts), new AnswerWriter(gateway, prompts),
            search);
    }

    private void givenSession() {
        when(interactions.getInteraction(KB, SESSION)).thenReturn(Optional.of(new Interaction(SESSION, KB, USER,
            Instant.EPOCH)));
        when(interactions.recentTurns(KB, SESSION, 5)).thenReturn(List.of());
        when(wiki.listIndex(KB)).thenReturn(List.of());
        when(wiki.liveSupersessionsOf(eq(KB), any())).thenReturn(List.of());
    }

    private static IndexEntry makeEntry(String path, String title, String description) {
        return new IndexEntry(path, title, description, PageType.CONCEPT);
    }

    private static WikiPage makePage(String path, String body) {
        return new WikiPage(UUID.randomUUID(), KB, path, path, "Description.", PageType.CONCEPT, body + "\n", 1, Instant.EPOCH,
            Instant.EPOCH);
    }
}
