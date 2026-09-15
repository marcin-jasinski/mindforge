package dev.mindforge.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** A recall rating on SM-2's 0–5 scale. */
public record ReviewRequest(@Min(0) @Max(5) int rating) {}
