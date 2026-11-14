package dev.mindforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import dev.mindforge.domain.model.ProcessingSettings;

/** In this package because {@code PipelineConfig.processingSettings} is package-private. */
class ProcessingSettingsBindingTest {

    // AC1

    @Test
    void withNoPropertySet_articleFetchingIsOff() {
        assertThat(makeSettings(Map.of()).fetchExternalArticles()).isFalse();
    }

    @Test
    void withTheSwitchSet_articleFetchingIsOn() {
        ProcessingSettings settings = makeSettings(Map.of("mindforge.articles.fetch-external-articles", "true"));

        assertThat(settings.fetchExternalArticles()).isTrue();
    }

    private static ProcessingSettings makeSettings(Map<String, String> properties) {
        Map<String, String> source = new HashMap<>(properties);
        source.put("mindforge.security.jwt-secret", "test-secret");
        AppProperties appProperties = new Binder(new MapConfigurationPropertySource(source))
            .bindOrCreate("mindforge", Bindable.of(AppProperties.class));
        return new PipelineConfig().processingSettings(appProperties);
    }
}
