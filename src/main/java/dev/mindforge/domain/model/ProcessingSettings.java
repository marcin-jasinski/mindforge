package dev.mindforge.domain.model;

import java.util.Map;

/**
 * Per-run pipeline tuning: chunking parameters, agent feature flags, and the
 * {@link ModelTier} to model-id mappings the gateway resolves against.
 */
public record ProcessingSettings(
    int chunkSizeTokens,
    int chunkOverlapTokens,
    Map<String, Boolean> featureFlags,
    Map<ModelTier, String> modelTierMappings
) {

    private static final int DEFAULT_CHUNK_SIZE_TOKENS = 800;
    private static final int DEFAULT_CHUNK_OVERLAP_TOKENS = 100;

    public ProcessingSettings {
        featureFlags = featureFlags == null ? Map.of() : Map.copyOf(featureFlags);
        modelTierMappings = modelTierMappings == null ? Map.of() : Map.copyOf(modelTierMappings);
    }

    public static ProcessingSettings defaults() {
        return new ProcessingSettings(
            DEFAULT_CHUNK_SIZE_TOKENS, DEFAULT_CHUNK_OVERLAP_TOKENS, Map.of(), Map.of());
    }

    public boolean isEnabled(String featureFlag) {
        return featureFlags.getOrDefault(featureFlag, false);
    }
}
