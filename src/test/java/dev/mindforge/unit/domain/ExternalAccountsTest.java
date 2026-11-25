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
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, ""));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.DISCORD, " "));
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
    void shouldKeepDiscordUnchangedAndAddSlack() {
        assertThat(ExternalPlatform.DISCORD.label()).isEqualTo("Discord");
        assertThat(ExternalPlatform.SLACK.label()).isEqualTo("Slack");
    }

    @Test
    void shouldBuildTheSlackExternalIdFromTheHomeTeamAndTheUser() {
        assertThat(ExternalAccounts.slackExternalId("T0123", "U0456")).isEqualTo("T0123-U0456");
    }

    @Test
    void shouldRefuseABlankNullOrNonAlphanumericSlackIdPart() {
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.slackExternalId("", "U0456"));
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.slackExternalId("T0123", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.slackExternalId(null, "U0456"));
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.slackExternalId("T0123", null));
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.slackExternalId("T0-123", "U0456"));
        assertThatIllegalArgumentException().isThrownBy(() -> ExternalAccounts.slackExternalId("T0123", "U04:56"));
    }

    @Test
    void shouldDeriveTheSyntheticEmailOfASlackUser() {
        assertThat(ExternalAccounts.syntheticEmail(ExternalPlatform.SLACK, "T0123-U0456"))
            .isEqualTo("slack-t0123-u0456@users.invalid");
    }

    @Test
    void shouldRefuseASlackIdWithoutExactlyOneTeamAndOneUser() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.SLACK, "T0123-"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.SLACK, "-U1"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.SLACK, "T1-U2-U3"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.SLACK, "T1:U2"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ExternalAccounts.syntheticEmail(ExternalPlatform.SLACK, "U0456"));
    }

    @Test
    void shouldReserveOnlyTheUsersInvalidDomain() {
        assertThat(ExternalAccounts.isReserved("x@users.invalid")).isTrue();
        assertThat(ExternalAccounts.isReserved("x@users.invalid.com")).isFalse();
        assertThat(ExternalAccounts.isReserved("x@example.com")).isFalse();
    }
}
