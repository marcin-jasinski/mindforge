package dev.mindforge.slack;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.domain.model.ExternalAccounts;
import dev.mindforge.domain.model.ExternalPlatform;

/**
 * The gate every slash command passes, in this order: team allowlist (ADR 0034), per-user rate limit, ack, then on the
 * executor resolve-or-provision the caller (ADR 0033) and the command. A rejected command only gets a fixed reply.
 */
public class SlackCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackCommandHandler.class);

    static final String NOT_AVAILABLE = "This bot is not available here.";
    static final String FAILED = "Something went wrong. Try again later.";

    private final SlackAllowlist allowlist;
    private final SlackRateLimiter rateLimiter;
    private final ExternalIdentityService identities;
    private final Map<String, SlackCommand> commands;
    private final Executor executor;

    public SlackCommandHandler(SlackAllowlist allowlist, SlackRateLimiter rateLimiter,
                               ExternalIdentityService identities, List<SlackCommand> commands, Executor executor) {
        this.allowlist = allowlist;
        this.rateLimiter = rateLimiter;
        this.identities = identities;
        this.commands = commands.stream()
            .collect(Collectors.toUnmodifiableMap(SlackCommand::name, Function.identity()));
        this.executor = executor;
    }

    /** Returns after the ack; identity resolution and the command run later on the executor. */
    public void handle(SlackCall call, SlackReply reply) {
        if (!allowlist.admits(call.workspaceTeamId(), call.callerTeamId())) {
            log.info("Slack command rejected: reason=team_not_allowed command={}", call.command());
            reply.send(NOT_AVAILABLE);
            return;
        }
        OptionalLong retryAfter = rateLimiter.tryAcquire(call.slackUserId());
        if (retryAfter.isPresent()) {
            reply.send("Rate limit reached. Try again in " + retryAfter.getAsLong() + " s.");
            return;
        }
        SlackCommand command = commands.get(call.command());
        if (command == null) {
            log.warn("Slack command unknown: command={}", call.command());
            reply.send(FAILED);
            return;
        }
        reply.ack();
        executor.execute(() -> run(command, call, reply));
    }

    private void run(SlackCommand command, SlackCall call, SlackReply reply) {
        try {
            UUID userId = identities.resolve(ExternalPlatform.SLACK,
                ExternalAccounts.slackExternalId(call.callerTeamId(), call.slackUserId()), call.displayName());
            command.handle(call, userId, reply);
        } catch (RuntimeException e) {
            log.warn("Slack command failed: command={} error={}", call.command(), e.getClass().getName());
            reply.send(FAILED);
        }
    }
}
