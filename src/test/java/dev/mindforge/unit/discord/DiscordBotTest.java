package dev.mindforge.unit.discord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import dev.mindforge.discord.DiscordBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;

class DiscordBotTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    // AC7

    @Test
    void run_waitsUntilJdaIsReady() throws Exception {
        JDA jda = mock(JDA.class);
        SelfUser self = mock(SelfUser.class);
        when(self.getName()).thenReturn("bot");
        when(jda.getSelfUser()).thenReturn(self);
        when(jda.getGuilds()).thenReturn(List.of());

        new DiscordBot(jda, WAIT).run(null);

        verify(jda).awaitReady();
    }

    @Test
    void close_shutsDownAndWaitsWithoutForcingWhenItFinishes() throws Exception {
        JDA jda = mock(JDA.class);
        when(jda.awaitShutdown(any(Duration.class))).thenReturn(true);

        new DiscordBot(jda, WAIT).close();

        InOrder order = inOrder(jda);
        order.verify(jda).shutdown();
        order.verify(jda).awaitShutdown(WAIT);
        verify(jda, never()).shutdownNow();
    }

    @Test
    void close_forcesShutdownWhenTheBoundedWaitExpires() throws Exception {
        JDA jda = mock(JDA.class);
        when(jda.awaitShutdown(any(Duration.class))).thenReturn(false);

        new DiscordBot(jda, WAIT).close();

        verify(jda).shutdownNow();
    }
}
