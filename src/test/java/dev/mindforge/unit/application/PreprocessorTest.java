package dev.mindforge.unit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mindforge.application.ingest.Preprocessor;
import dev.mindforge.domain.model.ContentBlock;

class PreprocessorTest {

    @Test
    void clean_keepsTheImageComponentOfARebuiltBlock() {
        List<ContentBlock> cleaned = Preprocessor.clean(List.of(
            ContentBlock.heading("Title", 1, 0),
            ContentBlock.text("Body  \r\n", 1)));

        assertThat(cleaned).extracting(ContentBlock::content).containsExactly("Title", "Body");
        assertThat(cleaned).extracting(ContentBlock::image).containsOnlyNulls();
    }
}
