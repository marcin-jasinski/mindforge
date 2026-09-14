package dev.mindforge.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Something a full Lint reports but never fixes: a finding (a contradiction, an unmarked supersession) or a
 * suggestion (a missing page, a question to investigate), naming the pages involved.
 */
public record ReviewItem(String kind, List<String> pages, String text) {

    public static final Set<String> FINDINGS = Set.of("contradiction", "unmarked_supersession");
    public static final Set<String> SUGGESTIONS = Set.of("missing_page", "question");

    public ReviewItem {
        pages = List.copyOf(pages);
    }

    /** The shape stored in {@code ingest_runs.findings}. */
    public Map<String, Object> toRow() {
        return Map.of("kind", kind, "pages", pages, "text", text);
    }
}
