package dev.mindforge.domain.model;

/**
 * The one token estimate: characters ÷ 3, rounded up. Polish tokenizes more densely than
 * English, and overestimating is the safe side of every budget that uses it.
 */
public final class TokenEstimate {

    private static final int CHARS_PER_TOKEN = 3;

    private TokenEstimate() {}

    public static int of(String text) {
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }
}
