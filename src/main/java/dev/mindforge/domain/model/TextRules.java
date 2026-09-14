package dev.mindforge.domain.model;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** The single normaliser for text that ends up on a structural line (titles, descriptions) and for page bodies. */
public final class TextRules {

    private static final Pattern INVISIBLE =
        Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\s]]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TRAILING_WHITESPACE =
        Pattern.compile("[\\s&&[^\\n]]+$", Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TRAILING_NEWLINES = Pattern.compile("\\n+$");

    private TextRules() {}

    /** Escapes {@code \}, {@code [} and {@code ]}, so text written as link text cannot break its link. */
    public static String escapeLinkText(String text) {
        return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    /** {@code \r\n} as {@code \n}, trailing whitespace stripped from every line, exactly one final newline. */
    public static String normaliseBody(String body) {
        String stripped = TRAILING_WHITESPACE.matcher(body.replace("\r\n", "\n")).replaceAll("");
        return TRAILING_NEWLINES.matcher(stripped).replaceAll("") + "\n";
    }

    /**
     * NFC, every whitespace run (including line breaks and tabs) as one space, remaining
     * control and format characters removed, trimmed.
     */
    public static String singleLine(String text) {
        String visible = INVISIBLE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll("");
        return WHITESPACE_RUN.matcher(visible).replaceAll(" ").trim();
    }
}
