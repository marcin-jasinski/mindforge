package dev.mindforge.unit.infrastructure.config;

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

class DiscordPropertiesTest {

    private static final LocalValidatorFactoryBean VALIDATOR = makeValidator();

    @AfterAll
    static void closeValidator() {
        VALIDATOR.close();
    }

    // AC2

    @Test
    void tokenAndCommaSeparatedIds_areBound() {
        AppProperties.Discord discord = bind(Map.of(
            "mindforge.discord.bot-token", "token",
            "mindforge.discord.allowed-guild-ids", "100,101",
            "mindforge.discord.allowed-role-ids", "200")).getDiscord();

        assertThat(discord.getBotToken()).isEqualTo("token");
        assertThat(discord.getAllowedGuildIds()).containsExactlyInAnyOrder(100L, 101L);
        assertThat(discord.getAllowedRoleIds()).containsExactly(200L);
    }

    @Test
    void emptyValues_bindToNoTokenAndEmptyLists() {
        AppProperties.Discord discord = bind(Map.of(
            "mindforge.discord.bot-token", "",
            "mindforge.discord.allowed-guild-ids", "",
            "mindforge.discord.allowed-role-ids", "")).getDiscord();

        assertThat(discord.getBotToken()).isNullOrEmpty();
        assertThat(discord.getAllowedGuildIds()).isEmpty();
        assertThat(discord.getAllowedRoleIds()).isEmpty();
    }

    @Test
    void aNonNumericId_failsBinding() {
        assertThatExceptionOfType(BindException.class).isThrownBy(() -> bind(Map.of(
            "mindforge.discord.bot-token", "token",
            "mindforge.discord.allowed-guild-ids", "my-guild")));
    }

    // AC3

    @Test
    void aTokenWithoutAllowedGuilds_failsValidation() {
        assertThatExceptionOfType(BindException.class)
            .isThrownBy(() -> bind(Map.of("mindforge.discord.bot-token", "token")))
            .satisfies(e -> assertThat(e).rootCause()
                .hasMessageContaining("allowed-guild-ids must not be empty when bot-token is set"));
    }

    @Test
    void noTokenAndNoAllowedGuilds_passes() {
        assertThat(bind(Map.of()).getDiscord().getAllowedGuildIds()).isEmpty();
    }

    @Test
    void aTokenWithOneAllowedGuild_passes() {
        AppProperties.Discord discord = bind(Map.of(
            "mindforge.discord.bot-token", "token",
            "mindforge.discord.allowed-guild-ids", "100")).getDiscord();

        assertThat(discord.getAllowedGuildIds()).containsExactly(100L);
        assertThat(discord.getAllowedRoleIds()).isEmpty();
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
