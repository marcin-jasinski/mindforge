package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.discord.DiscordAllowlist;
import dev.mindforge.discord.DiscordCall;
import dev.mindforge.discord.DiscordKnowledgeBases;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import net.dv8tion.jda.api.interactions.commands.Command;

class DiscordKnowledgeBasesTest {

    private static final long GUILD = 100L;
    private static final long DISCORD_USER = 300L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String UNKNOWN = "Unknown knowledge base.";

    private final KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final DiscordKnowledgeBases knowledgeBases = new DiscordKnowledgeBases(
        new DiscordAllowlist(Set.of(GUILD), Set.of()), identities,
            new KnowledgeBaseService(repository, mock(QuizSessionStore.class)));
    private final RecordingDiscordReply reply = new RecordingDiscordReply();

    // AC7

    @Test
    void anOwnedKnowledgeBase_resolvesToItsId() {
        KnowledgeBase kb = makeKnowledgeBase(USER_ID, "Biology");
        when(repository.findById(kb.kbId())).thenReturn(Optional.of(kb));

        assertThat(knowledgeBases.resolveKnowledgeBase(kb.kbId().toString(), USER_ID, reply)).contains(kb.kbId());
        assertThat(reply.events).isEmpty();
    }

    @Test
    void aValueThatIsNotAUuid_givesTheFixedReplyWithoutAServiceCall() {
        assertThat(knowledgeBases.resolveKnowledgeBase("Biology", USER_ID, reply)).isEmpty();

        assertThat(reply.events).containsExactly(UNKNOWN);
        verifyNoInteractions(repository);
    }

    @Test
    void anUnknownId_givesTheFixedReply() {
        UUID kbId = UUID.randomUUID();
        when(repository.findById(kbId)).thenReturn(Optional.empty());

        assertThat(knowledgeBases.resolveKnowledgeBase(kbId.toString(), USER_ID, reply)).isEmpty();
        assertThat(reply.events).containsExactly(UNKNOWN);
    }

    @Test
    void anotherUsersId_givesTheSameFixedReplyWithoutEchoingTheValue() {
        KnowledgeBase foreign = makeKnowledgeBase(UUID.randomUUID(), "Theirs");
        when(repository.findById(foreign.kbId())).thenReturn(Optional.of(foreign));

        assertThat(knowledgeBases.resolveKnowledgeBase(foreign.kbId().toString(), USER_ID, reply)).isEmpty();
        assertThat(reply.events).containsExactly(UNKNOWN);
    }

    // AC8

    @Test
    void autocomplete_listsTheCallersKnowledgeBasesWhoseNameContainsTheTypedText() {
        when(identities.resolve(ExternalPlatform.DISCORD, "300", "Ada")).thenReturn(USER_ID);
        KnowledgeBase biology = makeKnowledgeBase(USER_ID, "Biology");
        KnowledgeBase history = makeKnowledgeBase(USER_ID, "History");
        KnowledgeBase marineBiology = makeKnowledgeBase(USER_ID, "Marine BIOLOGY");
        when(repository.listByOwner(USER_ID)).thenReturn(List.of(biology, history, marineBiology));

        List<Command.Choice> choices = knowledgeBases.suggest(makeCall(GUILD), "bio");

        assertThat(choices).extracting(Command.Choice::getName, Command.Choice::getAsString)
            .containsExactly(
                tuple("Biology", biology.kbId().toString()),
                tuple("Marine BIOLOGY", marineBiology.kbId().toString()));
    }

    @Test
    void autocomplete_capsAt25ChoicesAndCutsNamesTo100Chars() {
        when(identities.resolve(any(), any(), any())).thenReturn(USER_ID);
        List<KnowledgeBase> many = IntStream.range(0, 30)
            .mapToObj(i -> makeKnowledgeBase(USER_ID, "x".repeat(150)))
            .toList();
        when(repository.listByOwner(USER_ID)).thenReturn(many);

        List<Command.Choice> choices = knowledgeBases.suggest(makeCall(GUILD), "");

        assertThat(choices).hasSize(25)
            .allSatisfy(choice -> assertThat(choice.getName()).hasSize(100));
    }

    @Test
    void autocompleteFromAGuildNotOnTheAllowlist_returnsNoChoicesAndProvisionsNothing() {
        assertThat(knowledgeBases.suggest(makeCall(999L), "")).isEmpty();
        assertThat(knowledgeBases.suggest(makeCall(null), "")).isEmpty();

        verifyNoInteractions(identities, repository);
    }

    private static DiscordCall makeCall(Long guildId) {
        return new DiscordCall(guildId, Set.of(), DISCORD_USER, "Ada", "ask", null, Map.of());
    }

    private static KnowledgeBase makeKnowledgeBase(UUID ownerId, String name) {
        return new KnowledgeBase(UUID.randomUUID(), ownerId, name, null, null, 0, 0);
    }
}
