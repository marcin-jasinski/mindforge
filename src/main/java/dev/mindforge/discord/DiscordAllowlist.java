package dev.mindforge.discord;

import java.util.Collections;
import java.util.Set;

/**
 * Who may use the bot (ADR 0028): a member of an allowed guild, holding one of the allowed roles when roles are
 * listed. A command without a guild (a DM) is never admitted.
 */
public record DiscordAllowlist(Set<Long> guildIds, Set<Long> roleIds) {

    public DiscordAllowlist {
        guildIds = Set.copyOf(guildIds);
        roleIds = Set.copyOf(roleIds);
    }

    public boolean admits(Long guildIdOrNull, Set<Long> memberRoleIds) {
        if (guildIdOrNull == null || !guildIds.contains(guildIdOrNull)) {
            return false;
        }
        return roleIds.isEmpty() || !Collections.disjoint(roleIds, memberRoleIds);
    }
}
