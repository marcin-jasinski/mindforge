package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import dev.mindforge.discord.JdaDiscordReply;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

class JdaDiscordReplyTest {

    private final IReplyCallback event = mock(IReplyCallback.class);
    private final InteractionHook hook = mock(InteractionHook.class);
    private final ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);
    @SuppressWarnings("unchecked")
    private final WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);

    JdaDiscordReplyTest() {
        when(event.deferReply(true)).thenReturn(replyAction);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(true)).thenReturn(replyAction);
        when(event.getHook()).thenReturn(hook);
        when(hook.sendMessage(anyString())).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);
    }

    // AC6

    @Test
    void defer_isEphemeral_andLaterTextsAreEphemeralFollowUps() {
        JdaDiscordReply reply = new JdaDiscordReply(event);

        reply.defer();
        reply.send("answer");

        verify(event).deferReply(true);
        verify(replyAction).complete();
        verify(hook).sendMessage("answer");
        verify(followUp).setEphemeral(true);
        verify(followUp).complete();
        verify(event, never()).reply(anyString());
    }

    @Test
    void aReplyWithoutDefer_isEphemeral() {
        new JdaDiscordReply(event).send("This bot is not available here.");

        verify(event).reply("This bot is not available here.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).complete();
        verify(hook, never()).sendMessage(anyString());
    }

    @Test
    void aTextOver2000Chars_goesOutAsOrderedEphemeralPartsOfAtMost2000Chars() {
        String text = "a".repeat(2000) + "b".repeat(2000) + "c";

        new JdaDiscordReply(event).send(text);

        InOrder order = inOrder(event, hook);
        order.verify(event).reply("a".repeat(2000));
        order.verify(hook).sendMessage("b".repeat(2000));
        order.verify(hook).sendMessage("c");
        verify(replyAction).setEphemeral(true);
        verify(followUp, times(2)).setEphemeral(true);
    }

    @Test
    void aSplit_neverCutsASurrogatePair() {
        String text = "a".repeat(1999) + "😀" + "b";
        ArgumentCaptor<String> parts = ArgumentCaptor.forClass(String.class);

        JdaDiscordReply reply = new JdaDiscordReply(event);
        reply.defer();
        reply.send(text);

        verify(hook, times(2)).sendMessage(parts.capture());
        List<String> sent = parts.getAllValues();
        assertThat(sent).allSatisfy(part -> assertThat(part.length()).isLessThanOrEqualTo(2000));
        assertThat(String.join("", sent)).isEqualTo(text);
        assertThat(sent.get(1)).startsWith("😀");
    }
}
