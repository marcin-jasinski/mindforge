package dev.mindforge.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A page's identity within its knowledge base: {@code concepts/<name>} or {@code sources/<lesson-id>}, the
 * bundle-relative path without {@code .md}. The directory is fixed by the page type and the name is always an
 * {@link Identifier}.
 */
public final class PagePath {

    public static final String CONCEPTS = "concepts";
    public static final String SOURCES = "sources";

    /** A path of the shared grammar; group 2 is the name. Unanchored so link destinations can embed it. */
    public static final Pattern PATTERN =
        Pattern.compile("(" + CONCEPTS + "|" + SOURCES + ")/(" + Identifier.PATTERN.pattern() + ")");

    private static final String RESERVED_SUFFIX = "-concept";

    private PagePath() {}

    /** The path of a new Concept, derived from its title; a name that lands on a reserved word gains a suffix. */
    public static String concept(String title) {
        String name = Identifier.slugify(title);
        return CONCEPTS + "/" + (Identifier.RESERVED.contains(name) ? name + RESERVED_SUFFIX : name);
    }

    /** The path of a lesson's Source Summary. The lesson id is explicit, so it is validated, never rewritten. */
    public static String sourceSummary(String lessonId) {
        String path = SOURCES + "/" + lessonId;
        if (!isValid(path)) {
            throw new IllegalArgumentException("Not a valid lesson id for a page path: '" + lessonId + "'");
        }
        return path;
    }

    public static boolean isValid(String path) {
        Matcher matcher = PATTERN.matcher(path);
        return matcher.matches() && !Identifier.RESERVED.contains(matcher.group(2));
    }
}
