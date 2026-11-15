package dev.mindforge.slack;

import java.util.Set;

/**
 * Who may use the bot (ADR 0034): a caller is admitted only when both the workspace the payload comes from and the
 * caller's own team are listed. A missing, blank or unlisted id is rejected. A DM carries both ids, so it is admitted
 * by the same check.
 */
public record SlackAllowlist(Set<String> teamIds) {

    public SlackAllowlist {
        teamIds = Set.copyOf(teamIds);
    }

    public boolean admits(String workspaceTeamIdOrNull, String callerTeamIdOrNull) {
        return listed(workspaceTeamIdOrNull) && listed(callerTeamIdOrNull);
    }

    private boolean listed(String teamIdOrNull) {
        return teamIdOrNull != null && !teamIdOrNull.isBlank() && teamIds.contains(teamIdOrNull);
    }
}
