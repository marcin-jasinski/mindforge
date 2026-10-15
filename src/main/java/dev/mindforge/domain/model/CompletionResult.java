package dev.mindforge.domain.model;

public record CompletionResult(
    String content,
    int inputTokens,
    int outputTokens,
    String model,
    String provider,
    long latencyMs,
    double costUsd
) {}
