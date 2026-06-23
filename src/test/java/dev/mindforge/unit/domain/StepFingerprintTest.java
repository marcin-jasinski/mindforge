package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.StepFingerprint;

class StepFingerprintTest {

    @Test
    void shouldBeDeterministicForSameInputs() {
        String a = StepFingerprint.compute("hash", "v1", "gpt-4o", "1.0");
        String b = StepFingerprint.compute("hash", "v1", "gpt-4o", "1.0");

        assertThat(a).isEqualTo(b).hasSize(16);
    }

    @Test
    void shouldChangeWhenAnyInputChanges() {
        String base = StepFingerprint.compute("hash", "v1", "gpt-4o", "1.0");

        assertThat(StepFingerprint.compute("other", "v1", "gpt-4o", "1.0")).isNotEqualTo(base);
        assertThat(StepFingerprint.compute("hash", "v2", "gpt-4o", "1.0")).isNotEqualTo(base);
        assertThat(StepFingerprint.compute("hash", "v1", "gpt-4o-mini", "1.0")).isNotEqualTo(base);
        assertThat(StepFingerprint.compute("hash", "v1", "gpt-4o", "1.1")).isNotEqualTo(base);
    }

    @Test
    void valueShouldMatchStaticCompute() {
        StepFingerprint fingerprint = new StepFingerprint("hash", "v1", "gpt-4o", "1.0");

        assertThat(fingerprint.value())
            .isEqualTo(StepFingerprint.compute("hash", "v1", "gpt-4o", "1.0"));
    }
}
