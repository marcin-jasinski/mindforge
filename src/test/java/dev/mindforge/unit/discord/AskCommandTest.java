package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.application.service.QueryService;
import dev.mindforge.discord.AskCommand;
import dev.mindforge.discord.DiscordAllowlist;
import dev.mindforge.discord.DiscordCall;
import dev.mindforge.discord.DiscordCommandHandler;
import dev.mindforge.discord.DiscordKnowledgeBases;
import dev.mindforge.discord.DiscordRateLimiter;
import dev.mindforge.domain.model.AIGatewayUnavailableException;
import dev.mindforge.domain.model.DeadlineExceededException;
import dev.mindforge.domain.model.DeadlineProfile;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.Interaction;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

class AskCommandTest {

    private static final long GUILD = 100L;
    private static final long DISCORD_USER = 300L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String QUESTION = "What is osmosis?";
    private static final String UNKNOWN = "Unknown knowledge base.";
    private static final String MODEL_UNAVAILABLE = "The model is not available now. Try again later.";
    private static final String SECRET = "provider said sk-secret";

    private final QueryService queryService = mock(QueryService.class);
    private final KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final DiscordKnowledgeBases knowledgeBases = new DiscordKnowledgeBases(
        new DiscordAllowlist(Set.of(GUILD), Set.of()), identities, new KnowledgeBaseService(repository));
    private final AskCommand command = new AskCommand(queryService, knowledgeBases);
    private final RecordingDiscordReply reply = new RecordingDiscordReply();

    // AC1

    @Test
    void definition_hasARequiredAutocompletedKbOptionAndARequiredQuestion() {
        assertThat(command.definition().getName()).isEqualTo("ask");
        assertThat(command.definition().getOptions())
            .extracting(OptionData::getName, OptionData::getType, OptionData::isRequired, OptionData::isAutoComplete)
            .containsExactly(
                tuple("kb", OptionType.STRING, true, true),
                tuple("question", OptionType.STRING, true, false));
    }

    // AC2

    @Test
    void anOwnedKnowledgeBase_startsAnInteractionAsksOnceAndRepliesWithAnswerAndSources() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(kb.kbId(), USER_ID, interaction.interactionId(), QUESTION)).thenReturn(
            new QueryService.Answer("Water moves across a membrane.", List.of("concepts/osmosis", "concepts/cell")));

        command.handle(makeCall(kb.kbId().toString()), USER_ID, reply);

        InOrder order = inOrder(queryService);
        order.verify(queryService).start(kb.kbId(), USER_ID);
        order.verify(queryService).ask(kb.kbId(), USER_ID, interaction.interactionId(), QUESTION);
        assertThat(reply.events).containsExactly(
            "Water moves across a membrane.\n\nSources:\n- concepts/osmosis\n- concepts/cell");
    }

    @Test
    void anAnswerWithoutCitedPaths_hasNoSourcesList() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(kb.kbId(), USER_ID, interaction.interactionId(), QUESTION))
            .thenReturn(new QueryService.Answer("The wiki has no page on that.", List.of()));

        command.handle(makeCall(kb.kbId().toString()), USER_ID, reply);

        assertThat(reply.events).containsExactly("The wiki has no page on that.");
    }

    // AC3

    @Test
    void twoAsks_startTwoInteractions() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Interaction first = makeInteraction(kb.kbId());
        Interaction second = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(first, second);
        when(queryService.ask(any(), any(), any(), any())).thenReturn(new QueryService.Answer("A.", List.of()));

        command.handle(makeCall(kb.kbId().toString()), USER_ID, reply);
        command.handle(makeCall(kb.kbId().toString()), USER_ID, reply);

        verify(queryService, times(2)).start(kb.kbId(), USER_ID);
        verify(queryService).ask(kb.kbId(), USER_ID, first.interactionId(), QUESTION);
        verify(queryService).ask(kb.kbId(), USER_ID, second.interactionId(), QUESTION);
    }

    // AC4

    @Test
    void aKbValueThatIsNotAUuid_givesTheFixedReplyAndNoQuery() {
        command.handle(makeCall("Biology"), USER_ID, reply);

        assertThat(reply.events).containsExactly(UNKNOWN);
        verifyNoInteractions(queryService);
    }

    @Test
    void anotherUsersKnowledgeBase_givesTheFixedReplyAndNoQuery() {
        KnowledgeBase foreign = new KnowledgeBase(UUID.randomUUID(), UUID.randomUUID(), "Theirs", null, null, 0, 0);
        when(repository.findById(foreign.kbId())).thenReturn(Optional.of(foreign));

        command.handle(makeCall(foreign.kbId().toString()), USER_ID, reply);

        assertThat(reply.events).containsExactly(UNKNOWN);
        verifyNoInteractions(queryService);
    }

    // AC5

    static List<RuntimeException> ownershipFailures() {
        return List.of(new NotOwnerException(UUID.randomUUID()), new NotFoundException(SECRET));
    }

    @ParameterizedTest
    @MethodSource("ownershipFailures")
    void anOwnershipFailureFromTheQuery_givesTheFixedReplyWithoutTheMessage(RuntimeException failure) {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(any(), any(), any(), any())).thenThrow(failure);

        command.handle(makeCall(kb.kbId().toString()), USER_ID, reply);

        assertThat(reply.events).containsExactly(UNKNOWN);
    }

    static List<RuntimeException> modelFailures() {
        return List.of(
            new AIGatewayUnavailableException(SECRET, new IllegalStateException(SECRET)),
            new DeadlineExceededException(DeadlineProfile.values()[0], Duration.ofSeconds(30)),
            new ModelOutputException("AnswerWriter", new IllegalStateException(SECRET)));
    }

    @ParameterizedTest
    @MethodSource("modelFailures")
    void aModelFailure_givesTheFixedReplyWithoutTheMessage(RuntimeException failure) {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(any(), any(), any(), any())).thenThrow(failure);

        command.handle(makeCall(kb.kbId().toString()), USER_ID, reply);

        assertThat(reply.events).containsExactly(MODEL_UNAVAILABLE);
    }

    // AC6

    @Test
    void throughTheGate_theReplyIsDeferredThroughTheEphemeralSeamThenCarriesOnlyTheAnswerAndPaths() {
        KnowledgeBase kb = makeOwnedKnowledgeBase();
        Interaction interaction = makeInteraction(kb.kbId());
        when(queryService.start(kb.kbId(), USER_ID)).thenReturn(interaction);
        when(queryService.ask(kb.kbId(), USER_ID, interaction.interactionId(), QUESTION))
            .thenReturn(new QueryService.Answer("Answer.", List.of("concepts/osmosis")));
        DiscordRateLimiter rateLimiter = mock(DiscordRateLimiter.class);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(OptionalLong.empty());
        when(identities.resolve(ExternalPlatform.DISCORD, "300", "Ada")).thenReturn(USER_ID);
        DiscordCommandHandler handler = new DiscordCommandHandler(new DiscordAllowlist(Set.of(GUILD), Set.of()),
            rateLimiter, identities, List.of(command));

        handler.handle(makeCall(kb.kbId().toString()), reply);

        assertThat(reply.events).containsExactly(RecordingDiscordReply.DEFER, "Answer.\n\nSources:\n- concepts/osmosis");
    }

    private KnowledgeBase makeOwnedKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase(UUID.randomUUID(), USER_ID, "Biology", null, null, 0, 0);
        when(repository.findById(kb.kbId())).thenReturn(Optional.of(kb));
        return kb;
    }

    private static Interaction makeInteraction(UUID kbId) {
        return new Interaction(UUID.randomUUID(), kbId, USER_ID, Instant.now());
    }

    private static DiscordCall makeCall(String kbOption) {
        return new DiscordCall(GUILD, Set.of(), DISCORD_USER, "Ada", "ask", null,
            Map.of("kb", kbOption, "question", QUESTION));
    }
}
