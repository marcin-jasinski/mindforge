package dev.mindforge.domain.model;

import static java.util.Map.entry;

import java.text.Normalizer;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one identifier grammar shared by lesson ids, page names and section anchors,
 * and the one function that derives an identifier from free text.
 */
public final class Identifier {

    public static final int MAX_LENGTH = 80;

    /**
     * {@code [a-z0-9]+(-[a-z0-9]+)*} of 1–{@value #MAX_LENGTH} characters, written without anchors
     * so a validator can embed it in a larger pattern and still get the length bound.
     */
    public static final Pattern PATTERN =
        Pattern.compile("[a-z0-9](?:[a-z0-9]|-(?=[a-z0-9])){0," + (MAX_LENGTH - 1) + "}");

    /** Reserved at every use (OKF §3.1). */
    public static final Set<String> RESERVED = Set.of("index", "log");

    /** Reserved for lesson ids, on top of {@link #RESERVED}. */
    public static final Set<String> RESERVED_LESSON_IDS = Set.of("default", "conversation");

    private static final Map<Integer, String> TRANSLITERATIONS = Map.ofEntries(
        entry((int) 'ł', "l"), entry((int) 'ø', "o"), entry((int) 'ß', "ss"), entry((int) 'đ', "d"),
        entry((int) 'æ', "ae"), entry((int) 'œ', "oe"), entry((int) 'þ', "th"),
        entry((int) 'α', "alfa"), entry((int) 'β', "beta"), entry((int) 'γ', "gamma"),
        entry((int) 'δ', "delta"), entry((int) 'ε', "epsilon"), entry((int) 'ζ', "dzeta"),
        entry((int) 'η', "eta"), entry((int) 'θ', "theta"), entry((int) 'ι', "jota"),
        entry((int) 'κ', "kappa"), entry((int) 'λ', "lambda"), entry((int) 'μ', "mi"),
        entry((int) 'ν', "ni"), entry((int) 'ξ', "ksi"), entry((int) 'ο', "omikron"),
        entry((int) 'π', "pi"), entry((int) 'ρ', "ro"), entry((int) 'σ', "sigma"),
        entry((int) 'ς', "sigma"), entry((int) 'τ', "tau"), entry((int) 'υ', "ypsilon"),
        entry((int) 'φ', "fi"), entry((int) 'χ', "chi"), entry((int) 'ψ', "psi"),
        entry((int) 'ω', "omega"));

    private Identifier() {}

    /** Whether {@code candidate} is an identifier of the shared grammar. */
    public static boolean matches(String candidate) {
        return PATTERN.matcher(candidate).matches();
    }

    /**
     * Derives an identifier from free text. Never returns empty, and never merges two
     * texts by silently dropping letters: a letter it cannot transliterate is dropped
     * and an 8-hex SHA-256 suffix of the text is appended.
     */
    public static String slugify(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC).trim();
        StringBuilder slug = new StringBuilder();
        boolean dropped = false;
        for (int codePoint : nfc.codePoints().toArray()) {
            int baseLetter = Character.toLowerCase(
                Normalizer.normalize(Character.toString(codePoint), Normalizer.Form.NFD).codePointAt(0));
            if (baseLetter < 128 && Character.isLetterOrDigit(baseLetter)) {
                slug.appendCodePoint(baseLetter);
            } else if (TRANSLITERATIONS.containsKey(baseLetter)) {
                slug.append(TRANSLITERATIONS.get(baseLetter));
            } else if (Character.isLetterOrDigit(codePoint)) {
                dropped = true;
            } else if (!slug.isEmpty() && slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
        }
        String stem = stripTrailingHyphen(slug.toString());
        String hash = Hashes.sha256Hex(nfc).substring(0, 8);
        if (stem.isEmpty()) {
            return "x-" + hash;
        }
        return dropped ? cap(stem, MAX_LENGTH - hash.length() - 1) + "-" + hash : cap(stem, MAX_LENGTH);
    }

    private static String cap(String slug, int maxLength) {
        return slug.length() <= maxLength ? slug : stripTrailingHyphen(slug.substring(0, maxLength));
    }

    private static String stripTrailingHyphen(String slug) {
        return slug.endsWith("-") ? slug.substring(0, slug.length() - 1) : slug;
    }
}
