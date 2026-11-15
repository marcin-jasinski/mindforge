package dev.mindforge.unit.discord;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import dev.mindforge.discord.JdaReminderSender;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

class JdaReminderSenderTest {

    // AC8

    @Test
    @SuppressWarnings("unchecked")
    void send_opensTheUsersPrivateChannelAndSendsTheText_blocking() {
        JDA jda = mock(JDA.class);
        User user = mock(User.class);
        PrivateChannel channel = mock(PrivateChannel.class);
        CacheRestAction<User> retrieveUser = mock(CacheRestAction.class);
        CacheRestAction<PrivateChannel> openChannel = mock(CacheRestAction.class);
        MessageCreateAction sendMessage = mock(MessageCreateAction.class);
        when(jda.retrieveUserById("111")).thenReturn(retrieveUser);
        when(retrieveUser.complete()).thenReturn(user);
        when(user.openPrivateChannel()).thenReturn(openChannel);
        when(openChannel.complete()).thenReturn(channel);
        when(channel.sendMessage("You have 2 flashcards due in *Biology*")).thenReturn(sendMessage);

        new JdaReminderSender(jda).send("111", "You have 2 flashcards due in *Biology*");

        InOrder order = inOrder(retrieveUser, openChannel, sendMessage);
        order.verify(retrieveUser).complete();
        order.verify(openChannel).complete();
        order.verify(sendMessage).complete();
    }
}
