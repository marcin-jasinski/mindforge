package dev.mindforge.unit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import dev.mindforge.infrastructure.config.AppProperties;

class AuthRateLimitPropertiesTest {

    private static final LocalValidatorFactoryBean VALIDATOR = makeValidator();

    @AfterAll
    static void closeValidator() {
        VALIDATOR.close();
    }

    // AC7

    @Test
    void defaults_areTenAttemptsPerMinute() {
        AppProperties.RateLimits.Auth auth = bind(Map.of()).getRateLimits().getAuth();

        assertThat(auth.getRequests()).isEqualTo(10);
        assertThat(auth.getWindow()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void configuredValues_areBoundWithoutChangingTheModelCallLimit() {
        AppProperties.RateLimits limits = bind(Map.of(
            "mindforge.rate-limits.auth.requests", "3",
            "mindforge.rate-limits.auth.window", "PT3S")).getRateLimits();

        assertThat(limits.getAuth().getRequests()).isEqualTo(3);
        assertThat(limits.getAuth().getWindow()).isEqualTo(Duration.ofSeconds(3));
        assertThat(limits.getRequests()).isEqualTo(30);
    }

    @Test
    void zeroRequests_isRejected() {
        assertThatExceptionOfType(BindException.class)
            .isThrownBy(() -> bind(Map.of("mindforge.rate-limits.auth.requests", "0")));
    }

    @Test
    void aNullWindow_isRejected() {
        AppProperties properties = bind(Map.of());
        properties.getRateLimits().getAuth().setWindow(null);

        assertThat(VALIDATOR.getValidator().validate(properties))
            .anySatisfy(violation ->
                assertThat(violation.getPropertyPath()).hasToString("rateLimits.auth.window"));
    }

    private static AppProperties bind(Map<String, String> properties) {
        Map<String, String> source = new HashMap<>(properties);
        source.put("mindforge.security.jwt-secret", "test-secret");
        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bindOrCreate("mindforge", Bindable.of(AppProperties.class),
            new ValidationBindHandler(VALIDATOR));
    }

    private static LocalValidatorFactoryBean makeValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
