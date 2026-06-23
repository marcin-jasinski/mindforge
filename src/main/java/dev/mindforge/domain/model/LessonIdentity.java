package dev.mindforge.domain.model;

import static java.util.Objects.requireNonNull;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic identity of a lesson: a stable {@code lessonId} slug plus a
 * human-readable {@code title}.
 *
 * <p>{@link #resolve(Map, String)} implements the five-step resolution algorithm.
 * The first non-blank source wins:
 * <ol>
 *   <li>Markdown frontmatter {@code lesson_id} (validated as-is)</li>
 *   <li>Markdown frontmatter {@code title} (slugified)</li>
 *   <li>PDF metadata {@code Title} (slugified)</li>
 *   <li>Filename stem (slugified)</li>
 *   <li>otherwise reject with {@link LessonIdentityException}</li>
 * </ol>
 * The resolved id must be 1–80 chars of {@code [a-z0-9-_]} and must not be a
 * reserved name. Identity never falls back to a placeholder.
 */
public record LessonIdentity(String lessonId, String title) {

    private static final int MAX_LENGTH = 80;
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9\\-_]+");
    private static final Pattern SLUG_SEPARATORS = Pattern.compile("[^a-z0-9_]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");
    private static final Set<String> RESERVED_NAMES = Set.of("index", "default");

    private static final String KEY_LESSON_ID = "lesson_id";
    private static final String KEY_FRONTMATTER_TITLE = "title";
    private static final String KEY_PDF_TITLE = "Title";

    public LessonIdentity {
        requireNonNull(lessonId, "lessonId");
        requireNonNull(title, "title");
    }

    public static LessonIdentity resolve(Map<String, String> metadata, String filename) {
        Map<String, String> meta = metadata == null ? Map.of() : metadata;

        String explicitId = trimToNull(meta.get(KEY_LESSON_ID));
        if (explicitId != null) {
            return new LessonIdentity(validate(explicitId), bestTitle(meta, filename, explicitId));
        }

        String frontmatterTitle = trimToNull(meta.get(KEY_FRONTMATTER_TITLE));
        if (frontmatterTitle != null) {
            return new LessonIdentity(validate(slugify(frontmatterTitle)), frontmatterTitle);
        }

        String pdfTitle = trimToNull(meta.get(KEY_PDF_TITLE));
        if (pdfTitle != null) {
            return new LessonIdentity(validate(slugify(pdfTitle)), pdfTitle);
        }

        String stem = stemOf(filename);
        if (stem != null) {
            return new LessonIdentity(validate(slugify(stem)), stem);
        }

        throw new LessonIdentityException(
            "Unable to resolve a lesson identity from metadata or filename");
    }

    private static String bestTitle(Map<String, String> meta, String filename, String fallback) {
        String title = trimToNull(meta.get(KEY_FRONTMATTER_TITLE));
        if (title != null) {
            return title;
        }
        title = trimToNull(meta.get(KEY_PDF_TITLE));
        if (title != null) {
            return title;
        }
        String stem = stemOf(filename);
        return stem != null ? stem : fallback;
    }

    private static String validate(String candidate) {
        if (candidate.isEmpty()) {
            throw new LessonIdentityException("Lesson id is empty after resolution");
        }
        if (candidate.length() > MAX_LENGTH) {
            throw new LessonIdentityException(
                "Lesson id exceeds " + MAX_LENGTH + " characters: " + candidate);
        }
        if (!VALID_ID.matcher(candidate).matches()) {
            throw new LessonIdentityException(
                "Lesson id contains illegal characters (allowed: a-z 0-9 - _): " + candidate);
        }
        if (RESERVED_NAMES.contains(candidate)) {
            throw new LessonIdentityException("Lesson id is a reserved name: " + candidate);
        }
        return candidate;
    }

    private static String slugify(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        String collapsed = SLUG_SEPARATORS.matcher(lower).replaceAll("-");
        String trimmed = EDGE_HYPHENS.matcher(collapsed).replaceAll("");
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = EDGE_HYPHENS.matcher(trimmed.substring(0, MAX_LENGTH)).replaceAll("");
        }
        return trimmed;
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
