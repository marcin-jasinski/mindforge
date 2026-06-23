package dev.mindforge.domain.model;

import static java.util.Objects.requireNonNull;

/**
 * The deterministic input fingerprint of a single pipeline step. Two runs with the
 * same inputs, prompt version, model and agent version produce the same
 * {@link #value()}, which lets the orchestrator skip unchanged steps.
 */
public record StepFingerprint(
    String inputHash,
    String promptVersion,
    String modelId,
    String agentVersion
) {

    private static final int FINGERPRINT_LENGTH = 16;

    public StepFingerprint {
        requireNonNull(inputHash, "inputHash");
        requireNonNull(promptVersion, "promptVersion");
        requireNonNull(modelId, "modelId");
        requireNonNull(agentVersion, "agentVersion");
    }

    /** The 16-char fingerprint stored on a {@link StepCheckpoint}. */
    public String value() {
        return compute(inputHash, promptVersion, modelId, agentVersion);
    }

    public static String compute(
        String inputHash, String promptVersion, String modelId, String agentVersion) {
        String material = inputHash + "|" + promptVersion + "|" + modelId + "|" + agentVersion;
        return Hashes.sha256Hex(material).substring(0, FINGERPRINT_LENGTH);
    }
}
