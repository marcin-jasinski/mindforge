package dev.mindforge.unit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import dev.mindforge.infrastructure.config.AppProperties;

class ArticlePropertiesTest {

    private static final LocalValidatorFactoryBean VALIDATOR = makeValidator();

    @AfterAll
    static void closeValidator() {
        VALIDATOR.close();
    }

    @Test
    void defaults_areTenArticles2MbFiveSecondsToConnectAndFifteenSecondsInTotal() {
        AppProperties.Articles articles = bind(Map.of()).getArticles();

        assertThat(articles.getMaxPerDocument()).isEqualTo(10);
        assertThat(articles.getMaxBodySize()).isEqualTo(DataSize.ofMegabytes(2));
        assertThat(articles.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(articles.getRequestTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void configuredValues_areBound() {
        AppProperties.Articles articles = bind(Map.of(
            "mindforge.articles.max-per-document", "3",
            "mindforge.articles.max-body-size", "512KB",
            "mindforge.articles.connect-timeout", "2s",
            "mindforge.articles.request-timeout", "PT30S")).getArticles();

        assertThat(articles.getMaxPerDocument()).isEqualTo(3);
        assertThat(articles.getMaxBodySize()).isEqualTo(DataSize.ofKilobytes(512));
        assertThat(articles.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(articles.getRequestTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @ParameterizedTest
    @CsvSource({
        "max-per-document, 0",
        "max-per-document, -1",
        "max-body-size, 0B",
        "connect-timeout, 0s",
        "connect-timeout, -1s",
        "request-timeout, 0s",
    })
    void aNonPositiveLimit_isRejected(String property, String value) {
        assertThatExceptionOfType(BindException.class)
            .isThrownBy(() -> bind(Map.of("mindforge.articles." + property, value)));
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
