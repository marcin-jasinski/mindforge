package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import dev.mindforge.discord.DiscordBot;
import dev.mindforge.discord.DiscordCall;
import dev.mindforge.discord.DiscordCommand;
import dev.mindforge.discord.DiscordCommandHandler;
import dev.mindforge.discord.DiscordKnowledgeBases;
import dev.mindforge.discord.DiscordListener;
import dev.mindforge.discord.DiscordReply;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

class DiscordBotTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private final DiscordListener listener = new DiscordListener(mock(DiscordCommandHandler.class),
        mock(DiscordKnowledgeBases.class));

    // AC7 (P18-04)

    @Test
    void run_waitsUntilJdaIsReady() throws Exception {
        JDA jda = makeReadyJda();

        new DiscordBot(jda, listener, List.of(), WAIT).run(null);

        verify(jda).awaitReady();
    }

    @Test
    void close_shutsDownAndWaitsWithoutForcingWhenItFinishes() throws Exception {
        JDA jda = mock(JDA.class);
        when(jda.awaitShutdown(any(Duration.class))).thenReturn(true);

        new DiscordBot(jda, listener, List.of(), WAIT).close();

        InOrder order = inOrder(jda);
        order.verify(jda).shutdown();
        order.verify(jda).awaitShutdown(WAIT);
        verify(jda, never()).shutdownNow();
    }

    @Test
    void close_forcesShutdownWhenTheBoundedWaitExpires() throws Exception {
        JDA jda = mock(JDA.class);
        when(jda.awaitShutdown(any(Duration.class))).thenReturn(false);

        new DiscordBot(jda, listener, List.of(), WAIT).close();

        verify(jda).shutdownNow();
    }

    // AC9

    @Test
    void run_attachesTheListenerAndRegistersEveryCommandForGuildsOnly() throws Exception {
        JDA jda = makeReadyJda();
        CommandListUpdateAction update = mock(CommandListUpdateAction.class);
        when(jda.updateCommands()).thenReturn(update);
        when(update.addCommands(anyCollection())).thenReturn(update);

        new DiscordBot(jda, listener, List.of(makeCommand("one"), makeCommand("two")), WAIT).run(null);

        InOrder order = inOrder(jda, update);
        order.verify(jda).addEventListener(listener);
        order.verify(jda).awaitReady();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CommandData>> registered = ArgumentCaptor.forClass(Collection.class);
        order.verify(update).addCommands(registered.capture());
        order.verify(update).complete();
        assertThat(registered.getValue())
            .extracting(CommandData::getName)
            .containsExactly("one", "two");
        assertThat(registered.getValue())
            .allSatisfy(data -> assertThat(data.getContexts()).containsExactly(InteractionContextType.GUILD));
    }

    private static JDA makeReadyJda() {
        JDA jda = mock(JDA.class);
        SelfUser self = mock(SelfUser.class);
        when(self.getName()).thenReturn("bot");
        when(jda.getSelfUser()).thenReturn(self);
        when(jda.getGuilds()).thenReturn(List.of());
        CommandListUpdateAction update = mock(CommandListUpdateAction.class);
        when(jda.updateCommands()).thenReturn(update);
        when(update.addCommands(anyCollection())).thenReturn(update);
        return jda;
    }

    private static DiscordCommand makeCommand(String name) {
        return new DiscordCommand() {
            @Override
            public SlashCommandData definition() {
                return Commands.slash(name, "A command");
            }

            @Override
            public void handle(DiscordCall call, UUID userId, DiscordReply reply) {
            }
        };
    }
}
