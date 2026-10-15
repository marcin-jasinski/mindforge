package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.TokenEstimate;

class TokenEstimateTest {

    @Test
    void shouldEstimateOneTokenPerThreeCharactersRoundedUp() {
        assertThat(TokenEstimate.of("")).isZero();
        assertThat(TokenEstimate.of("abc")).isEqualTo(1);
        assertThat(TokenEstimate.of("abcd")).isEqualTo(2);
        assertThat(TokenEstimate.of("a".repeat(36_000))).isEqualTo(12_000);
    }
}
