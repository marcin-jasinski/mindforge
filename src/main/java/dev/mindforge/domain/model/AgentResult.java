package dev.mindforge.domain.model;

/**
 * The outcome of an {@link Agent#execute(AgentContext)} call. A discriminated union:
 * either a {@link Success} carrying usage metrics, or a {@link Failure} that tells
 * the orchestrator whether a retry is worthwhile.
 */
public sealed interface AgentResult permits AgentResult.Success, AgentResult.Failure {

    record Success(
        String outputKey,
        int tokensUsed,
        double costUsd,
        long durationMs
    ) implements AgentResult {}

    record Failure(
        String error,
        boolean retryable
    ) implements AgentResult {}
}
