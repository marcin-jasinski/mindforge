package dev.mindforge.domain.model;

/** {@code costUsd} is what the provider charged; {@code null} when it sent no cost (not measured, never 0). */
public record CompletionResult(
    String content,
    int inputTokens,
    int outputTokens,
    String model,
    String provider,
    long latencyMs,
    Double costUsd
) {}
