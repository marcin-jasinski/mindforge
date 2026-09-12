package dev.mindforge.domain.model;

import java.util.Map;

/**
 * Per-run pipeline tuning: chunk size, feature flags, and the {@link ModelTier} to model-id
 * mappings the gateway resolves against. Chunks never overlap — Extract is their only consumer,
 * and overlap would duplicate claims.
 */
public record ProcessingSettings(
    int chunkSizeTokens,
    Map<String, Boolean> featureFlags,
    Map<ModelTier, String> modelTierMappings
) {

    private static final int DEFAULT_CHUNK_SIZE_TOKENS = 12_000;

    public ProcessingSettings {
        featureFlags = featureFlags == null ? Map.of() : Map.copyOf(featureFlags);
        modelTierMappings = modelTierMappings == null ? Map.of() : Map.copyOf(modelTierMappings);
    }

    public static ProcessingSettings defaults() {
        return new ProcessingSettings(DEFAULT_CHUNK_SIZE_TOKENS, Map.of(), Map.of());
    }

    public boolean isEnabled(String featureFlag) {
        return featureFlags.getOrDefault(featureFlag, false);
    }
}
