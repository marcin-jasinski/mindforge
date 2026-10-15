package dev.mindforge.domain.model;

import static java.util.Objects.requireNonNull;

/**
 * Immutable content fingerprint used for deduplication and revision detection.
 * Wraps the lowercase hex SHA-256 of the raw document bytes.
 */
public record ContentHash(String sha256) {

    public ContentHash {
        requireNonNull(sha256, "sha256");
    }

    public static ContentHash compute(byte[] raw) {
        requireNonNull(raw, "raw");
        return new ContentHash(Hashes.sha256Hex(raw));
    }
}
