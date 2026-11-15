package dev.mindforge.slack;

import java.util.Locale;
import java.util.Optional;

/**
 * The grammar of the text sent with an uploaded file: whitespace-separated tokens {@code lesson_id=<value>} and
 * {@code new_version}, each at most once, keys case-insensitive. Anything else is refused.
 */
public final class SlackUploadText {

    static final String GRAMMAR = "The message text may hold only lesson_id=<value> and new_version.";

    private static final String LESSON_ID = "lesson_id=";
    private static final String NEW_VERSION = "new_version";

    /** The options the text sets; {@code lessonId} is null when the text names none. */
    public record UploadOptions(String lessonId, boolean newVersion) {
    }

    private SlackUploadText() {
    }

    /** The options, or empty when the text breaks the grammar. A null or blank text sets no option. */
    public static Optional<UploadOptions> parse(String text) {
        String lessonId = null;
        boolean newVersion = false;
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty()) {
            return Optional.of(new UploadOptions(null, false));
        }
        for (String token : stripped.split("\\s+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.equals(NEW_VERSION) && !newVersion) {
                newVersion = true;
            } else if (lower.startsWith(LESSON_ID) && token.length() > LESSON_ID.length() && lessonId == null) {
                lessonId = token.substring(LESSON_ID.length());
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(new UploadOptions(lessonId, newVersion));
    }
}
