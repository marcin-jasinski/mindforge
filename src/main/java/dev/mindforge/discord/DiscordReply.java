package dev.mindforge.discord;

/** The one way a command answers its caller; every reply is ephemeral (ADR 0032). */
public interface DiscordReply {

    /** Acknowledges the command before slow work; later texts replace the loading state. */
    void defer();

    /** Sends the text, split into ordered messages when it is longer than Discord allows. */
    void send(String text);
}
