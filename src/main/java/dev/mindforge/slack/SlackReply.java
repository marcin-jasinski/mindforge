package dev.mindforge.slack;

/** The one way a command answers its caller; every reply goes only to the caller (ephemeral, ADR 0032). */
public interface SlackReply {

    /** Acknowledges the command without text, before slow work; later texts go out as separate messages. */
    void ack();

    /** Sends the text, split into ordered messages when it is longer than one message may be. */
    void send(String text);
}
