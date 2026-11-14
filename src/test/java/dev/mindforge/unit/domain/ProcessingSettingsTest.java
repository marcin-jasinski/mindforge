package dev.mindforge.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ModelTier;
import dev.mindforge.domain.model.ProcessingSettings;

class ProcessingSettingsTest {

    @Test
    void fetchExternalArticles_isOffByDefault() {
        assertThat(ProcessingSettings.defaults().fetchExternalArticles()).isFalse();
        assertThat(ProcessingSettings.withModels(Map.of(ModelTier.LARGE, "large-model")).fetchExternalArticles())
            .isFalse();
    }

    @Test
    void withFetchExternalArticles_returnsACopyThatChangesOnlyTheSwitch() {
        ProcessingSettings settings = ProcessingSettings.withModels(Map.of(ModelTier.LARGE, "large-model"));

        ProcessingSettings switchedOn = settings.withFetchExternalArticles(true);

        assertThat(switchedOn.fetchExternalArticles()).isTrue();
        assertThat(switchedOn).usingRecursiveComparison().ignoringFields("fetchExternalArticles").isEqualTo(settings);
        assertThat(settings.fetchExternalArticles()).isFalse();
    }
}
