package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mindforge.discord.DiscordAllowlist;

class DiscordAllowlistTest {

    private static final long GUILD = 100L;
    private static final long ROLE = 200L;

    // AC4

    @Test
    void aCommandFromADm_isRejected() {
        assertThat(makeAllowlist(Set.of()).admits(null, Set.of())).isFalse();
    }

    @Test
    void aCommandFromAnotherGuild_isRejected() {
        assertThat(makeAllowlist(Set.of()).admits(101L, Set.of())).isFalse();
    }

    @Test
    void anyMemberOfAnAllowedGuild_isAdmittedWhenNoRoleIsListed() {
        assertThat(makeAllowlist(Set.of()).admits(GUILD, Set.of())).isTrue();
    }

    @Test
    void aMemberWithoutAListedRole_isRejected() {
        assertThat(makeAllowlist(Set.of(ROLE)).admits(GUILD, Set.of(201L))).isFalse();
    }

    @Test
    void aMemberWithOneListedRole_isAdmitted() {
        assertThat(makeAllowlist(Set.of(ROLE, 202L)).admits(GUILD, Set.of(201L, ROLE))).isTrue();
    }

    private static DiscordAllowlist makeAllowlist(Set<Long> roleIds) {
        return new DiscordAllowlist(Set.of(GUILD), roleIds);
    }
}
