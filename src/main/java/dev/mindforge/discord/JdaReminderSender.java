package dev.mindforge.discord;

import net.dv8tion.jda.api.JDA;

import dev.mindforge.domain.port.ReminderSender;

/** Sends a reminder as a Discord direct message, blocking until Discord accepts it. */
public class JdaReminderSender implements ReminderSender {

    private final JDA jda;

    public JdaReminderSender(JDA jda) {
        this.jda = jda;
    }

    @Override
    public void send(String externalUserId, String text) {
        jda.retrieveUserById(externalUserId).complete()
            .openPrivateChannel().complete()
            .sendMessage(text).complete();
    }
}
