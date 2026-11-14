package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ExternalAccounts;
import dev.mindforge.domain.model.ExternalPlatform;

class ExternalAccountsTest {

    @Test
    void shouldDeriveTheSyntheticEmailOfADiscordUser() {
        assertThat(ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, "123456789012345678"))
            .isEqualTo("discord-123456789012345678@users.invalid");
    }

    @Test
    void shouldRefuseABlankIdAndAnIdOutsideLettersAndDigits() {
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, " "));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, "12@evil.com"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, "12-34"));
    }

    @Test
    void shouldNeverReturnMoreThan255Characters() {
        String longest = "a".repeat(255 - "discord-@users.invalid".length());

        assertThat(ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, longest)).hasSize(255);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, longest + "a"));
    }

    @Test
    void shouldReserveOnlyTheUsersInvalidDomain() {
        assertThat(ExternalAccounts.isReserved("x@users.invalid")).isTrue();
        assertThat(ExternalAccounts.isReserved("x@users.invalid.com")).isFalse();
        assertThat(ExternalAccounts.isReserved("x@example.com")).isFalse();
    }
}
