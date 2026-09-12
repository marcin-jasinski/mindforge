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
        String text = "WSTĘP\r\n\r\nMitoza to podział komórki.\r\nDruga linia akapitu.\r\n \r\n"
            + "1. Faza profazy\n\nW profazie chromatyna się kondensuje.\n\nkrótka linia bez kropki\n";

        ParsedDocument parsed = parser.parse(text.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.blocks()).containsExactly(
            ContentBlock.heading("WSTĘP", 1, 0),
            ContentBlock.text("Mitoza to podział komórki.\nDruga linia akapitu.", 1),
            ContentBlock.heading("1. Faza profazy", 1, 2),
            ContentBlock.text("W profazie chromatyna się kondensuje.", 3),
            ContentBlock.text("krótka linia bez kropki", 4));
        assertThat(parsed.text()).isEqualTo(text);
        assertThat(parsed.metadata()).isEmpty();
    }

    @Test
    void shouldDropAByteOrderMark() {
        ParsedDocument parsed = parser.parse("﻿Tekst notatki.".getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.text()).isEqualTo("Tekst notatki.");
    }

    @Test
    void shouldRejectBytesThatAreNotUtf8() {
        byte[] windows1250 = {'M', 'i', 't', 'o', 'z', 'a', ' ', (byte) 0xB3};

        assertThatExceptionOfType(UploadRejectedException.class).isThrownBy(() -> parser.parse(windows1250));
    }
}
