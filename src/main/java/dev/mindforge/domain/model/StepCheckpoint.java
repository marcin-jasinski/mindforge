package dev.mindforge.domain.model;

import java.time.Instant;

/**
 * A persisted record that a pipeline step completed, keyed by its output and the
 * input fingerprint that produced it. Used for resume-on-rerun semantics.
 */
public record StepCheckpoint(
    String outputKey,
    String fingerprint,
    Instant completedAt
) {}
