package dev.mindforge.discord;

import java.util.Map;
import java.util.Set;

/** A slash command or autocomplete request, translated out of JDA. {@code guildId} is null for a DM. */
public record DiscordCall(
    Long guildId,
    Set<Long> memberRoleIds,
    long discordUserId,
    String displayName,
    String command,
    String subcommand,
    Map<String, String> options
) {

    public DiscordCall {
        memberRoleIds = Set.copyOf(memberRoleIds);
        options = Map.copyOf(options);
    }
}
