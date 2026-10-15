package dev.mindforge.unit.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;
import dev.mindforge.infrastructure.parsing.PlainTextParser;

class PlainTextParserTest {

    private final PlainTextParser parser = new PlainTextParser();

    @Test
    void shouldSplitParagraphsOnBlankLinesAndDetectShortUnpunctuatedLinesAsHeadings() {
        String text = "INTRODUCTION\r\n\r\nMitosis is cell division.\r\nSecond line of the paragraph.\r\n \r\n"
            + "1. Prophase\n\nIn prophase, chromatin condenses.\n\na short line with no full stop\n";

        ParsedDocument parsed = parser.parse(text.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.heading("INTRODUCTION", 1, 0),
            ContentBlock.text("Mitosis is cell division.\nSecond line of the paragraph.", 1),
            ContentBlock.heading("1. Prophase", 1, 2),
            ContentBlock.text("In prophase, chromatin condenses.", 3),
            ContentBlock.text("a short line with no full stop", 4));
        assertThat(parsed.text()).isEqualTo(text);
        assertThat(parsed.metadata()).isEmpty();
    }

    @Test
    void shouldDropAByteOrderMark() {
        ParsedDocument parsed = parser.parse("﻿Note text.".getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.text()).isEqualTo("Note text.");
    }

    @Test
    void shouldRejectBytesThatAreNotUtf8() {
        byte[] windows1250 = {'M', 'i', 't', 'o', 'z', 'a', ' ', (byte) 0xB3};

        assertThatExceptionOfType(UploadRejectedException.class).isThrownBy(() -> parser.parse(windows1250));
    }
}
