package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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

class SlackPropertiesTest {

    private static final LocalValidatorFactoryBean VALIDATOR = makeValidator();

    @AfterAll
    static void closeValidator() {
        VALIDATOR.close();
    }

    // AC2

    @Test
    void bothTokens_areBoundAndPass() {
        AppProperties.Slack slack = bind(Map.of(
            "mindforge.slack.bot-token", "xoxb-placeholder",
            "mindforge.slack.app-token", "xapp-placeholder")).getSlack();

        assertThat(slack.getBotToken()).isEqualTo("xoxb-placeholder");
        assertThat(slack.getAppToken()).isEqualTo("xapp-placeholder");
    }

    @Test
    void neitherToken_passes() {
        AppProperties.Slack slack = bind(Map.of(
            "mindforge.slack.bot-token", "",
            "mindforge.slack.app-token", "")).getSlack();

        assertThat(slack.getBotToken()).isNullOrEmpty();
        assertThat(slack.getAppToken()).isNullOrEmpty();
    }

    @Test
    void aBotTokenWithoutAnAppToken_failsValidation() {
        assertThatExceptionOfType(BindException.class)
            .isThrownBy(() -> bind(Map.of("mindforge.slack.bot-token", "xoxb-placeholder")))
            .satisfies(e -> assertThat(e).rootCause()
                .hasMessageContaining("app-token must be set when bot-token is set")
                .hasMessageNotContaining("xoxb-placeholder"));
    }

    @Test
    void anAppTokenWithoutABotToken_failsValidation() {
        assertThatExceptionOfType(BindException.class)
            .isThrownBy(() -> bind(Map.of(
                "mindforge.slack.bot-token", " ",
                "mindforge.slack.app-token", "xapp-placeholder")))
            .satisfies(e -> assertThat(e).rootCause()
                .hasMessageContaining("bot-token must be set when app-token is set")
                .hasMessageNotContaining("xapp-placeholder"));
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
