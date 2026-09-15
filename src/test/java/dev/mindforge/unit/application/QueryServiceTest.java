package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.mindforge.agent.AnswerWriter;
import dev.mindforge.agent.PageSelector;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.InteractionTurn;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.model.PageLink;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.WikiPage;
import dev.mindforge.domain.port.InteractionStore;
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

    @Test
    void shouldDropHallucinatedPathsAddLinkedNeighboursAndCiteOnlyPagesItWasGiven() {
        WikiPage mitoza = makePage("concepts/mitoza", "Mitoza dzieli komórkę.");
        WikiPage mejoza = makePage("concepts/mejoza", "Mejoza wytwarza gamety.");
        givenSession();
        when(wiki.findByPaths(KB, List.of("concepts/mitoza", "concepts/zmyslona"))).thenReturn(List.of(mitoza));
        when(wiki.outboundLinks(KB, List.of(mitoza.pageId())))
            .thenReturn(List.of(new PageLink(mitoza.pageId(), "concepts/mejoza", null)));
        when(wiki.findByPaths(KB, List.of("concepts/mejoza"))).thenReturn(List.of(mejoza));
        gateway.answer(Prompts.SELECT, "{\"paths\": [\"concepts/mitoza\", \"concepts/zmyslona\"]}");
        gateway.answer(Prompts.ANSWER, "{\"answer\": \"Mitoza dzieli komórkę.\","
            + " \"citations\": [\"concepts/mitoza\", \"concepts/zmyslona\"]}");

        QueryService.Answer answer = makeService().ask(KB, USER, SESSION, "Co to mitoza?");

        assertThat(answer.citedPaths()).containsExactly("concepts/mitoza");
        assertThat(gateway.recordedCalls().getLast().prompt()).contains("Mitoza dzieli komórkę.")
            .contains("Mejoza wytwarza gamety.");
        ArgumentCaptor<InteractionTurn> turn = ArgumentCaptor.forClass(InteractionTurn.class);
        verify(interactions).addTurn(eq(KB), eq(SESSION), turn.capture());
        assertThat(turn.getValue().usedPagePaths()).containsExactly("concepts/mitoza", "concepts/mejoza");
    }

    @Test
    void shouldStopAddingContextAtTheFirstPageThatDoesNotFitTheBudget() {
        WikiPage small = makePage("concepts/mitoza", "Mała strona.");
        WikiPage huge = makePage("concepts/wielka", "x".repeat(90_000));
        WikiPage after = makePage("concepts/mejoza", "Strona po wielkiej.");
        givenSession();
        when(wiki.findByPaths(eq(KB), any())).thenReturn(List.of(small, huge, after));
        when(wiki.outboundLinks(eq(KB), any())).thenReturn(List.of());
        gateway.answer(Prompts.SELECT, "{\"paths\": [\"concepts/mitoza\", \"concepts/wielka\", \"concepts/mejoza\"]}");
        gateway.answer(Prompts.ANSWER, "{\"answer\": \"Odpowiedź.\", \"citations\": []}");

        makeService().ask(KB, USER, SESSION, "Pytanie?");

        assertThat(gateway.recordedCalls().getLast().prompt()).contains("Mała strona.").doesNotContain("xxxxxxxxxx")
            .doesNotContain("Strona po wielkiej.");
    }

    @Test
    void shouldRefuseAnotherUsersConversation() {
        givenSession();

        assertThatExceptionOfType(NotOwnerException.class)
            .isThrownBy(() -> makeService().ask(KB, UUID.randomUUID(), SESSION, "Pytanie?"));
    }

    private QueryService makeService() {
        PromptLoader prompts = new PromptLoader("pl");
        return new QueryService(wiki, interactions, new PageSelector(gateway, prompts), new AnswerWriter(gateway, prompts));
    }

    private void givenSession() {
        when(interactions.getInteraction(KB, SESSION)).thenReturn(Optional.of(new Interaction(SESSION, KB, USER,
            Instant.EPOCH)));
        when(interactions.turns(KB, SESSION)).thenReturn(List.of());
        when(wiki.listIndex(KB)).thenReturn(List.of());
        when(wiki.liveSupersessionsOf(eq(KB), any())).thenReturn(List.of());
    }

    private static WikiPage makePage(String path, String body) {
        return new WikiPage(UUID.randomUUID(), KB, path, path, "Opis.", PageType.CONCEPT, body + "\n", 1, Instant.EPOCH,
            Instant.EPOCH);
    }
}
