package dev.mindforge.slack;

import java.util.UUID;

/** A slash command. It runs only after {@link SlackCommandHandler} admitted the caller and resolved the user. */
public interface SlackCommand {

    /** The slash-command name as configured in the Slack app, for example {@code /mf-ask}; it routes calls here. */
    String name();

    void handle(SlackCall call, UUID userId, SlackReply reply);
}
