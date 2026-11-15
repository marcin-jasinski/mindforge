package dev.mindforge.discord;

import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.Message;

/**
 * A slash command or autocomplete request, translated out of JDA. {@code guildId} is null for a DM.
 * {@code attachments} holds the attachment options by name; they stay JDA objects because only
 * {@link DiscordAttachments} may download them.
 */
public record DiscordCall(
    Long guildId,
    Set<Long> memberRoleIds,
    long discordUserId,
    String displayName,
    String command,
    String subcommand,
    Map<String, String> options,
    Map<String, Message.Attachment> attachments
) {

    public DiscordCall {
        memberRoleIds = Set.copyOf(memberRoleIds);
        options = Map.copyOf(options);
        attachments = Map.copyOf(attachments);
    }

    /** A call without attachments. */
    public DiscordCall(Long guildId, Set<Long> memberRoleIds, long discordUserId, String displayName, String command,
                       String subcommand, Map<String, String> options) {
        this(guildId, memberRoleIds, discordUserId, displayName, command, subcommand, options, Map.of());
    }
}
