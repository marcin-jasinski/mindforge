package dev.mindforge.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the real gateway in integration tests, so no test ever calls a provider. */
@TestConfiguration
public class StubAiConfig {

    @Bean
    @Primary
    StubAIGateway stubAIGateway() {
        return new StubAIGateway();
    }
}
