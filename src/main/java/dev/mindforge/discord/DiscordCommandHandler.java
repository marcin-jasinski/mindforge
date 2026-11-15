package dev.mindforge.discord;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.domain.model.ExternalPlatform;

/**
 * The gate every slash command passes, in this order: guild and role allowlist (ADR 0028), per-user rate limit,
 * resolve-or-provision the caller (ADR 0027), the command. A rejected command only gets a fixed reply.
 */
public class DiscordCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(DiscordCommandHandler.class);

    static final String NOT_AVAILABLE = "This bot is not available here.";
    static final String FAILED = "Something went wrong. Try again later.";

    private final DiscordAllowlist allowlist;
    private final DiscordRateLimiter rateLimiter;
    private final ExternalIdentityService identities;
    private final Map<String, DiscordCommand> commands;

    public DiscordCommandHandler(DiscordAllowlist allowlist, DiscordRateLimiter rateLimiter,
                                 ExternalIdentityService identities, List<DiscordCommand> commands) {
        this.allowlist = allowlist;
        this.rateLimiter = rateLimiter;
        this.identities = identities;
        this.commands = commands.stream()
            .collect(Collectors.toUnmodifiableMap(command -> command.definition().getName(), Function.identity()));
    }

    public void handle(DiscordCall call, DiscordReply reply) {
        if (!allowlist.admits(call.guildId(), call.memberRoleIds())) {
            log.info("Discord command rejected: guild={}", call.guildId() == null ? "dm" : call.guildId());
            reply.send(NOT_AVAILABLE);
            return;
        }
        OptionalLong retryAfter = rateLimiter.tryAcquire(call.discordUserId());
        if (retryAfter.isPresent()) {
            reply.send("Rate limit reached. Try again in " + retryAfter.getAsLong() + " s.");
            return;
        }
        DiscordCommand command = commands.get(call.command());
        if (command == null) {
            log.warn("Discord command unknown: command={}", call.command());
            reply.send(FAILED);
            return;
        }
        try {
            reply.defer();
            UUID userId = identities.resolve(ExternalPlatform.DISCORD, String.valueOf(call.discordUserId()),
                call.displayName());
            command.handle(call, userId, reply);
        } catch (RuntimeException e) {
            log.warn("Discord command failed: command={} error={}", call.command(), e.getClass().getName());
            reply.send(FAILED);
        }
    }
}
