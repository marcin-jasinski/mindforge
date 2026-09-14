package dev.mindforge.domain.model;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Deterministic identity of a lesson: a stable {@code lessonId} plus a
 * human-readable {@code title}.
 *
 * <p>{@link #resolve(Map, String)} takes the first source present:
 * <ol>
 *   <li>Markdown frontmatter {@code lesson_id} — must already match the {@link Identifier}
 *       grammar; never rewritten</li>
 *   <li>Markdown frontmatter {@code title} (slugified)</li>
 *   <li>PDF metadata {@code Title} (slugified)</li>
 *   <li>Filename stem (slugified)</li>
 *   <li>otherwise reject with {@link LessonIdentityException}</li>
 * </ol>
 * {@code index}, {@code log}, {@code default} and {@code conversation} are reserved: an
 * explicit id using one is rejected, a derived one gains {@code -lesson}. The title passes
 * {@link TextRules#singleLine} and is cut to 200 characters. Identity never falls back to a
 * placeholder.
 */
public record LessonIdentity(String lessonId, String title) {

    /** Metadata key of an explicit lesson id: Markdown frontmatter, or the upload's override. */
    public static final String KEY_LESSON_ID = "lesson_id";
    /** Metadata key of a PDF document's title. */
    public static final String KEY_PDF_TITLE = "Title";

    private static final int MAX_TITLE_LENGTH = 200;
    private static final String ELLIPSIS = "…";
    private static final String RESERVED_SUFFIX = "-lesson";

    private static final String KEY_FRONTMATTER_TITLE = "title";

    public LessonIdentity {
        requireNonNull(lessonId, "lessonId");
        requireNonNull(title, "title");
    }

    public static LessonIdentity resolve(Map<String, String> metadata, String filename) {
        Map<String, String> meta = metadata == null ? Map.of() : metadata;
        String title = Stream.of(meta.get(KEY_FRONTMATTER_TITLE), meta.get(KEY_PDF_TITLE), stemOf(filename))
            .filter(Objects::nonNull)
            .map(TextRules::singleLine)
            .filter(candidate -> !candidate.isEmpty())
            .findFirst()
            .orElse(null);

        String explicitId = meta.get(KEY_LESSON_ID);
        if (explicitId != null && !explicitId.isBlank()) {
            return new LessonIdentity(validate(explicitId), cut(title != null ? title : explicitId));
        }
        if (title == null) {
            throw new LessonIdentityException(
                "Unable to resolve a lesson identity from metadata or filename");
        }
        return new LessonIdentity(derive(Identifier.slugify(title)), cut(title));
    }

    private static String validate(String candidate) {
        if (!Identifier.matches(candidate)) {
            throw new LessonIdentityException(
                "Lesson id must be 1-" + Identifier.MAX_LENGTH
                    + " characters of a-z, 0-9 and single inner hyphens: '" + candidate + "'");
        }
        if (isReserved(candidate)) {
            throw new LessonIdentityException("Lesson id is a reserved name: " + candidate);
        }
        return candidate;
    }

    /** A derived id is never rejected: {@code slugify} already obeys the grammar, and a reserved word is suffixed. */
    private static String derive(String slug) {
        return isReserved(slug) ? slug + RESERVED_SUFFIX : slug;
    }

    private static boolean isReserved(String lessonId) {
        return Identifier.RESERVED.contains(lessonId) || Identifier.RESERVED_LESSON_IDS.contains(lessonId);
    }

    /** A lesson title is metadata, not model output, so it is cut rather than rejected. */
    private static String cut(String title) {
        if (title.codePointCount(0, title.length()) <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, MAX_TITLE_LENGTH - 1)) + ELLIPSIS;
    }

    private static String stemOf(String filename) {
        String name = trimToNull(filename);
        if (name == null) {
            return null;
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return trimToNull(name);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
