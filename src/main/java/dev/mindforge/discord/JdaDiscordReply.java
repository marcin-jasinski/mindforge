package dev.mindforge.discord;

import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

/**
 * The only class that answers an interaction through JDA. Every defer, reply and follow-up is ephemeral (ADR 0032),
 * and a text over Discord's message limit goes out as ordered parts.
 */
public class JdaDiscordReply implements DiscordReply {

    static final int MAX_MESSAGE_LENGTH = 2000;

    private final IReplyCallback interaction;
    private boolean acknowledged;

    public JdaDiscordReply(IReplyCallback interaction) {
        this.interaction = interaction;
    }

    @Override
    public void defer() {
        interaction.deferReply(true).complete();
        acknowledged = true;
    }

    @Override
    public void send(String text) {
        int start = 0;
        do {
            int end = Math.min(text.length(), start + MAX_MESSAGE_LENGTH);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            sendPart(text.substring(start, end));
            start = end;
        } while (start < text.length());
    }

    private void sendPart(String part) {
        if (acknowledged) {
            interaction.getHook().sendMessage(part).setEphemeral(true).complete();
        } else {
            interaction.reply(part).setEphemeral(true).complete();
            acknowledged = true;
        }
    }
}
