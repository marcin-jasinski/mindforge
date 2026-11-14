package dev.mindforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import dev.mindforge.infrastructure.event.ArticleFetchTriggers;

/** In this package because the bean methods are package-private. */
class ArticleFetchConfigWiringTest {

    // AC5

    @Test
    void articleFetchTriggers_isNotABeanInTheCliProfile() {
        Profile profile = Arrays.stream(ArticleFetchConfig.class.getDeclaredMethods())
            .filter(m -> m.getReturnType() == ArticleFetchTriggers.class)
            .findFirst().orElseThrow()
            .getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!cli");
    }
}
