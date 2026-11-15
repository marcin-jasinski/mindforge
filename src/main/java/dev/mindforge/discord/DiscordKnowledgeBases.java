package dev.mindforge.discord;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.NotOwnerException;

/**
 * The {@code kb} option (ADR 0030): its definition, its autocomplete over the caller's own knowledge bases, and its
 * resolution to a knowledge base the caller owns. The value is untrusted; the ownership check is the boundary.
 */
public class DiscordKnowledgeBases {

    public static final String OPTION = "kb";
    static final String UNKNOWN = "Unknown knowledge base.";

    private static final int MAX_CHOICES = 25;
    private static final int MAX_CHOICE_NAME_LENGTH = 100;

    private final DiscordAllowlist allowlist;
    private final ExternalIdentityService identities;
    private final KnowledgeBaseService knowledgeBases;

    public DiscordKnowledgeBases(DiscordAllowlist allowlist, ExternalIdentityService identities,
                                 KnowledgeBaseService knowledgeBases) {
        this.allowlist = allowlist;
        this.identities = identities;
        this.knowledgeBases = knowledgeBases;
    }

    /** The required, autocompleted {@code kb} option for {@code /ask}, {@code /quiz start} and {@code /upload}. */
    public static OptionData option() {
        return new OptionData(OptionType.STRING, OPTION, "Knowledge base", true, true);
    }

    /**
     * The knowledge base the option names, when the user owns it. Otherwise replies "Unknown knowledge base." and
     * returns empty; the reply is the same for a malformed, unknown or foreign id.
     */
    public Optional<UUID> resolveKnowledgeBase(String kbOption, UUID userId, DiscordReply reply) {
        Optional<UUID> kbId = ownedKnowledgeBase(kbOption, userId);
        if (kbId.isEmpty()) {
            reply.send(UNKNOWN);
        }
        return kbId;
    }

    /** Choices for the caller's own knowledge bases whose name contains the typed text; none outside the allowlist. */
    public List<Command.Choice> suggest(DiscordCall call, String typed) {
        if (!allowlist.admits(call.guildId(), call.memberRoleIds())) {
            return List.of();
        }
        UUID userId = identities.resolve(ExternalPlatform.DISCORD, String.valueOf(call.discordUserId()),
            call.displayName());
        String needle = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return knowledgeBases.list(userId).stream()
            .filter(kb -> kb.name().toLowerCase(Locale.ROOT).contains(needle))
            .limit(MAX_CHOICES)
            .map(DiscordKnowledgeBases::choice)
            .toList();
    }

    private Optional<UUID> ownedKnowledgeBase(String kbOption, UUID userId) {
        if (kbOption == null) {
            return Optional.empty();
        }
        UUID kbId;
        try {
            kbId = UUID.fromString(kbOption);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        try {
            knowledgeBases.requireOwner(kbId, userId);
            return Optional.of(kbId);
        } catch (NotFoundException | NotOwnerException e) {
            return Optional.empty();
        }
    }

    private static Command.Choice choice(KnowledgeBase kb) {
        String name = kb.name();
        if (name.length() > MAX_CHOICE_NAME_LENGTH) {
            int end = Character.isHighSurrogate(name.charAt(MAX_CHOICE_NAME_LENGTH - 1))
                ? MAX_CHOICE_NAME_LENGTH - 1 : MAX_CHOICE_NAME_LENGTH;
            name = name.substring(0, end);
        }
        return new Command.Choice(name, kb.kbId().toString());
    }
}
