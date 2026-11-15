package dev.mindforge.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

/** In this package because the token condition is package-private. */
class DiscordConfigWiringTest {

    // AC6

    @Test
    void theDiscordConfiguration_isNotLoadedInTheCliProfile() {
        Profile profile = DiscordConfig.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!cli");
    }

    @Test
    void theDiscordConfiguration_isLoadedWhenTheBotTokenIsSet() {
        assertThat(tokenConditionMatches(new MockEnvironment()
            .withProperty("mindforge.discord.bot-token", "token"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void theDiscordConfiguration_isNotLoadedWhenTheBotTokenIsBlank(String token) {
        assertThat(tokenConditionMatches(new MockEnvironment()
            .withProperty("mindforge.discord.bot-token", token))).isFalse();
    }

    @Test
    void theDiscordConfiguration_isNotLoadedWithoutABotToken() {
        assertThat(tokenConditionMatches(new MockEnvironment())).isFalse();
    }

    private static boolean tokenConditionMatches(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return new DiscordConfig.BotTokenSet().matches(context, mock(AnnotatedTypeMetadata.class));
    }
}
