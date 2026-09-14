package dev.mindforge.domain.model;

import static java.util.Objects.requireNonNull;

import java.util.List;

/**
 * The kind of a page, assigned by code. Normalised to a single line; the two types MindForge
 * produces are matched ignoring case, and any other value read from a bundle is kept as it is (OKF §4.1).
 */
public record PageType(String value) {

    /** Declared before the constants below, whose construction reads it. */
    private static final List<String> KNOWN = List.of("Concept", "Source Summary");

    public static final PageType CONCEPT = new PageType("Concept");
    public static final PageType SOURCE_SUMMARY = new PageType("Source Summary");

    public PageType {
        String normalised = TextRules.singleLine(requireNonNull(value, "value"));
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("A page type must not be blank");
        }
        value = KNOWN.stream().filter(normalised::equalsIgnoreCase).findFirst().orElse(normalised);
    }
}
