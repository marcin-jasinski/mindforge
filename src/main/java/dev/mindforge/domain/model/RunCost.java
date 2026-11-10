package dev.mindforge.domain.model;

import java.util.Map;

/**
 * What the provider charged for a run's responses: {@code totalUsd} is {@code null} when no call reported a cost
 * (not measured, never 0). {@code byService} is keyed by model service, as in {@code step_versions}.
 */
public record RunCost(Double totalUsd, Map<String, Double> byService) {

    public RunCost {
        byService = Map.copyOf(byService);
    }

    public static RunCost of(Map<String, Double> byService) {
        return new RunCost(byService.isEmpty() ? null
            : byService.values().stream().mapToDouble(Double::doubleValue).sum(), byService);
    }
}
