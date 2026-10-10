package dev.mindforge.domain.model;

/**
 * Something a run dropped, skipped or could not do, recorded on the run rather than thrown. Which components are
 * set depends on the step: {@code item} and {@code path} name what was dropped, {@code reason} says why, and a
 * step that only counts sets {@code dropped} or {@code omittedPages}. The component names are the stored JSON keys.
 */
public record RunFailure(String step, String item, String path, String reason, Integer dropped,
                         Integer omittedPages) {

    /** A step that failed as a whole. */
    public static RunFailure of(String step, String reason) {
        return new RunFailure(step, null, null, reason, null, null);
    }

    /** One page's write that failed. */
    public static RunFailure ofPath(String step, String path, String reason) {
        return new RunFailure(step, null, path, reason, null, null);
    }

    /** One proposed item that was dropped, with the path it named. */
    public static RunFailure ofItem(String step, String item, String path, String reason) {
        return new RunFailure(step, item, path, reason, null, null);
    }

    /** A step that dropped {@code count} proposals. */
    public static RunFailure dropped(String step, int count) {
        return new RunFailure(step, null, null, null, count, null);
    }

    /** A step that left {@code count} candidate pages out of its context. */
    public static RunFailure omittedPages(String step, int count) {
        return new RunFailure(step, null, null, null, null, count);
    }
}
