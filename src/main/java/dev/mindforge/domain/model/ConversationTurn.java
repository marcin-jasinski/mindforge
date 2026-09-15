package dev.mindforge.domain.model;

/**
 * The content of a conversation edit's document: the learner's instruction, followed — for "save that" — by the
 * answer it quotes.
 */
public record ConversationTurn(String instruction, String quotedAnswer) {

    // ponytail: first marker wins, so an instruction that itself contains the marker splits early
    private static final String QUOTE_MARKER = "\n\n---\n\n";

    public String content() {
        return quotedAnswer == null ? instruction : instruction + QUOTE_MARKER + quotedAnswer;
    }

    public static ConversationTurn parse(String content) {
        int marker = content.indexOf(QUOTE_MARKER);
        return marker < 0
            ? new ConversationTurn(content, null)
            : new ConversationTurn(content.substring(0, marker), content.substring(marker + QUOTE_MARKER.length()));
    }
}
