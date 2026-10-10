package dev.mindforge.unit.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ModelOutputException;
import dev.mindforge.infrastructure.ai.ModelJson;

class ModelJsonTest {

    record Draft(String description, String body) {}

    @Test
    void shouldReadACleanObject() {
        assertThat(read("{\"description\":\"a\",\"body\":\"b\"}")).isEqualTo(new Draft("a", "b"));
    }

    @Test
    void shouldReadAnObjectWrappedInProse() {
        assertThat(read("Oto wynik: {\"description\":\"a\",\"body\":\"b\"} Pozdrawiam."))
            .isEqualTo(new Draft("a", "b"));
    }

    @Test
    void shouldReadAnObjectInAFencedCodeBlock() {
        assertThat(read("```json\n{\"description\":\"a\",\"body\":\"b\"}\n```")).isEqualTo(new Draft("a", "b"));
    }

    @Test
    void shouldReadTheFirstOfTwoObjects() {
        assertThat(read("Oto wynik: {\"description\":\"a\",\"body\":\"b\"} oraz {\"note\":\"ignore\"}"))
            .isEqualTo(new Draft("a", "b"));
    }

    @Test
    void shouldNameTheServiceWhenThereIsNoJson() {
        assertThatThrownBy(() -> read("brak obiektu"))
            .isInstanceOf(ModelOutputException.class)
            .hasMessageContaining("PageWriter");
    }

    @Test
    void shouldLeaveAMissingFieldNullForTheServiceToReject() {
        assertThat(read("{\"description\":\"a\"}")).isEqualTo(new Draft("a", null));
    }

    private static Draft read(String completion) {
        return ModelJson.read("PageWriter", completion, Draft.class);
    }
}
