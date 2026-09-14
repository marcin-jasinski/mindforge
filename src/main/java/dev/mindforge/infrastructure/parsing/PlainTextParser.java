package dev.mindforge.infrastructure.parsing;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dev.mindforge.domain.model.ContentBlock;
import dev.mindforge.domain.model.ParsedDocument;
import dev.mindforge.domain.model.UploadRejectedException;

/**
 * Plain text as paragraphs separated by blank lines. A paragraph is taken for a heading when it is one
 * short line that starts with a capital letter or digit and does not end like a sentence — a best effort,
 * since plain text has no markup to say so.
 */
public class PlainTextParser implements FormatParser {

    public static final String MIME_TYPE = "text/plain";

    private static final Pattern BLANK_LINES = Pattern.compile("\n\\s*\n");
    private static final int MAX_HEADING_LENGTH = 80;
    private static final String SENTENCE_ENDINGS = ".,;:!?";
    private static final char BYTE_ORDER_MARK = '﻿';

    @Override
    public ParsedDocument parse(byte[] content) {
        String text = decodeUtf8(content);
        List<ContentBlock> blocks = new ArrayList<>();
        for (String paragraph : BLANK_LINES.split(text.replace("\r\n", "\n"))) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            blocks.add(looksLikeHeading(trimmed)
                ? ContentBlock.heading(trimmed, 1, blocks.size())
                : ContentBlock.text(trimmed, blocks.size()));
        }
        return new ParsedDocument(text, blocks, Map.of());
    }

    /** Strict UTF-8: text in another encoding is refused rather than silently garbled. */
    static String decodeUtf8(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new UploadRejectedException("The document is not UTF-8 text", e);
        }
    }

    private static boolean looksLikeHeading(String paragraph) {
        int first = paragraph.codePointAt(0);
        return paragraph.indexOf('\n') < 0
            && paragraph.length() <= MAX_HEADING_LENGTH
            && SENTENCE_ENDINGS.indexOf(paragraph.charAt(paragraph.length() - 1)) < 0
            && (Character.isUpperCase(first) || Character.isDigit(first));
    }
}
