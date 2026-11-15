package dev.mindforge.slack;

/**
 * A slash command, translated out of Bolt. {@code workspaceTeamId} is the payload's {@code team_id};
 * {@code callerTeamId} is the caller's own team ({@code user_team} when present, else {@code team_id}). {@code command}
 * is the name with its slash, for example {@code /mf-ask}; {@code text} is everything the caller typed after it.
 */
public record SlackCall(
    String workspaceTeamId,
    String callerTeamId,
    String slackUserId,
    String displayName,
    String command,
    String text
) {
}
