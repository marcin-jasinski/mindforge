package dev.mindforge.domain.model;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** The single normaliser for text that ends up on a structural line (titles, descriptions). */
public final class TextRules {

    private static final Pattern INVISIBLE =
        Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\s]]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private TextRules() {}

    /**
     * NFC, every whitespace run (including line breaks and tabs) as one space, remaining
     * control and format characters removed, trimmed.
     */
    public static String singleLine(String text) {
        String visible = INVISIBLE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll("");
        return WHITESPACE_RUN.matcher(visible).replaceAll(" ").trim();
    }
}
