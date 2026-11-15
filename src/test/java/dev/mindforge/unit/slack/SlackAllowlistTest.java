package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mindforge.slack.SlackAllowlist;

class SlackAllowlistTest {

    private static final String TEAM = "T0HOME";
    private static final String OTHER_TEAM = "T0OTHER";

    // AC1

    @Test
    void aListedWorkspaceAndAListedCallerTeam_areAdmitted() {
        assertThat(makeAllowlist(TEAM, OTHER_TEAM).admits(TEAM, OTHER_TEAM)).isTrue();
    }

    @Test
    void aDmWithTheSameListedTeamInBothIds_isAdmitted() {
        assertThat(makeAllowlist(TEAM).admits(TEAM, TEAM)).isTrue();
    }

    @Test
    void aListedWorkspaceWithAForeignCallerTeam_isRejected() {
        assertThat(makeAllowlist(TEAM).admits(TEAM, OTHER_TEAM)).isFalse();
    }

    @Test
    void aNullOrBlankCallerTeam_isRejected() {
        SlackAllowlist allowlist = makeAllowlist(TEAM, " ");

        assertThat(allowlist.admits(TEAM, null)).isFalse();
        assertThat(allowlist.admits(TEAM, " ")).isFalse();
        assertThat(allowlist.admits(TEAM, "")).isFalse();
    }

    @Test
    void anUnlistedOrMissingWorkspace_isRejected() {
        SlackAllowlist allowlist = makeAllowlist(TEAM);

        assertThat(allowlist.admits(OTHER_TEAM, TEAM)).isFalse();
        assertThat(allowlist.admits(null, TEAM)).isFalse();
    }

    @Test
    void anEmptyAllowlist_admitsNobody() {
        assertThat(makeAllowlist().admits(TEAM, TEAM)).isFalse();
    }

    private static SlackAllowlist makeAllowlist(String... teamIds) {
        return new SlackAllowlist(Set.of(teamIds));
    }
}
