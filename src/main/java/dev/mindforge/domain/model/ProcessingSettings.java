package dev.mindforge.domain.model;

import java.util.Map;

/**
 * Pipeline tuning: chunk size, the two caps that fail a run loudly, the writer's and Supersede's input budgets,
 * feature flags, and the {@link ModelTier} to model-id mappings recorded on every run. Chunks never overlap —
 * Extract is their only consumer, and overlap would duplicate claims.
 */
public record ProcessingSettings(
    int chunkSizeTokens,
    int maxClaimsPerExtractCall,
    int maxPageTasksPerRun,
    int writerSourceTokens,
    int supersessionContextTokens,
    int cardPagesPerSession,
    Map<String, Boolean> featureFlags,
    Map<ModelTier, String> modelTierMappings
) {

    private static final int DEFAULT_CHUNK_SIZE_TOKENS = 12_000;
    private static final int DEFAULT_MAX_CLAIMS_PER_EXTRACT_CALL = 40;
    private static final int DEFAULT_MAX_PAGE_TASKS_PER_RUN = 100;
    private static final int DEFAULT_WRITER_SOURCE_TOKENS = 16_000;
    private static final int DEFAULT_SUPERSESSION_CONTEXT_TOKENS = 30_000;
    private static final int DEFAULT_CARD_PAGES_PER_SESSION = 10;

    public ProcessingSettings {
        featureFlags = featureFlags == null ? Map.of() : Map.copyOf(featureFlags);
        modelTierMappings = modelTierMappings == null ? Map.of() : Map.copyOf(modelTierMappings);
    }

    public static ProcessingSettings defaults() {
        return withModels(Map.of());
    }

    /** The defaults, recording these model ids. */
    public static ProcessingSettings withModels(Map<ModelTier, String> modelTierMappings) {
        return new ProcessingSettings(DEFAULT_CHUNK_SIZE_TOKENS, DEFAULT_MAX_CLAIMS_PER_EXTRACT_CALL,
            DEFAULT_MAX_PAGE_TASKS_PER_RUN, DEFAULT_WRITER_SOURCE_TOKENS, DEFAULT_SUPERSESSION_CONTEXT_TOKENS,
            DEFAULT_CARD_PAGES_PER_SESSION, Map.of(), modelTierMappings);
    }

    public boolean isEnabled(String featureFlag) {
        return featureFlags.getOrDefault(featureFlag, false);
    }

    /** What {@code step_versions} records for a model service: its version and the model its tier routes to. */
    public String stepVersion(String version, ModelTier tier) {
        return version + "@" + modelTierMappings.getOrDefault(tier, tier.name());
    }
}
