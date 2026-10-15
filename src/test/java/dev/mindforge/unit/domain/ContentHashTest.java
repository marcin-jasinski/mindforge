package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ContentHash;

class ContentHashTest {

    @Test
    void shouldBeDeterministicForSameInput() {
        byte[] raw = "lesson content".getBytes(StandardCharsets.UTF_8);

        assertThat(ContentHash.compute(raw)).isEqualTo(ContentHash.compute(raw));
    }

    @Test
    void shouldProduceDifferentHashesForDifferentInput() {
        ContentHash a = ContentHash.compute("alpha".getBytes(StandardCharsets.UTF_8));
        ContentHash b = ContentHash.compute("beta".getBytes(StandardCharsets.UTF_8));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldProduceLowercaseHex64CharDigest() {
        ContentHash hash = ContentHash.compute(new byte[0]);

        assertThat(hash.sha256())
            .hasSize(64)
            .matches("[0-9a-f]{64}")
            // Known SHA-256 of the empty input.
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
