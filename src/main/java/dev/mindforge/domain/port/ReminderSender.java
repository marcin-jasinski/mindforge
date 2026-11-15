package dev.mindforge.domain.port;

/** Sends a direct message to a user of a chat platform, addressed by the platform's own user id. */
public interface ReminderSender {

    void send(String externalUserId, String text);
}
