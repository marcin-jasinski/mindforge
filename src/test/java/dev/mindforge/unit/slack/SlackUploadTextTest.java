package dev.mindforge.unit.slack;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.mindforge.slack.SlackUploadText;
import dev.mindforge.slack.SlackUploadText.UploadOptions;

class SlackUploadTextTest {

    // AC6

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\n"})
    void noText_setsNoOption(String text) {
        assertThat(SlackUploadText.parse(text)).contains(new UploadOptions(null, false));
    }

    @Test
    void lessonIdAndNewVersion_areReadWithCaseInsensitiveKeysAndTheValueKept() {
        assertThat(SlackUploadText.parse(" Lesson_ID=Osmosis-1  NEW_VERSION "))
            .contains(new UploadOptions("Osmosis-1", true));
        assertThat(SlackUploadText.parse("lesson_id=osmosis")).contains(new UploadOptions("osmosis", false));
        assertThat(SlackUploadText.parse("new_version")).contains(new UploadOptions(null, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lesson_id=", "please upload", "new_version=true", "lesson_id osmosis",
        "lesson_id=a lesson_id=b", "new_version new_version"})
    void aTokenOutsideTheGrammar_isRefused(String text) {
        assertThat(SlackUploadText.parse(text)).isEmpty();
    }
}
