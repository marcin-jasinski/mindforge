package dev.mindforge.slack;

import java.util.List;

/**
 * A {@code message} event, translated out of Bolt. {@code workspaceTeamId} is the event's {@code team}, else the
 * envelope's {@code team_id}; {@code callerTeamId} is the event's {@code user_team} only (null when absent, which the
 * allowlist rejects). {@code botIdOrNull} is set when a bot sent the message.
 */
public record SlackMessage(
    String workspaceTeamId,
    String callerTeamId,
    String slackUserId,
    String channelId,
    String channelType,
    String subtype,
    String botIdOrNull,
    String text,
    List<File> files
) {

    public SlackMessage {
        files = List.copyOf(files);
    }

    /** A shared file as the event lists it; {@code size} is the size Slack declares, 0 when absent. */
    public record File(String name, String mimeType, long size, String urlPrivateDownload) {
    }
}
