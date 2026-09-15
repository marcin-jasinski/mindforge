package dev.mindforge.application.wiki;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.PageType;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.model.TokenEstimate;

/**
 * Renders the root {@code index.md}: {@code okf_version} frontmatter, then {@code # Concepts} and {@code # Sources},
 * always both, each listing {@code * [title](/path.md) - description} in Polish collation order, ties broken by path.
 * The same text feeds model prompts, the SPA and export.
 */
public final class IndexRenderer {

    private static final Logger log = LoggerFactory.getLogger(IndexRenderer.class);

    private static final String FRONTMATTER = "---\nokf_version: \"0.1\"\n---\n";
    /** Past this many tokens of index, the lexical prefilter is due (ADR 0016). */
    public static final int RETRIEVAL_CEILING_TOKENS = 20_000;

    private static final Locale POLISH = Locale.forLanguageTag("pl");

    private IndexRenderer() {}

    public static String render(List<IndexEntry> entries) {
        Comparator<IndexEntry> order = Comparator.comparing(IndexEntry::title, Collator.getInstance(POLISH))
            .thenComparing(IndexEntry::path);
        StringBuilder index = new StringBuilder(FRONTMATTER);
        appendSection(index, "Concepts", PageType.CONCEPT, entries, order);
        index.append('\n');
        appendSection(index, "Sources", PageType.SOURCE_SUMMARY, entries, order);

        String rendered = index.toString();
        int tokens = TokenEstimate.of(rendered);
        if (tokens > RETRIEVAL_CEILING_TOKENS) {
            // ponytail: warn only; the lexical prefilter (ADR 0016) replaces this when an index first gets here
            log.warn("Index of {} pages is {} tokens, past the {}-token retrieval ceiling",
                entries.size(), tokens, RETRIEVAL_CEILING_TOKENS);
        }
        return rendered;
    }

    private static void appendSection(StringBuilder index, String heading, PageType type, List<IndexEntry> entries,
                                      Comparator<IndexEntry> order) {
        index.append("# ").append(heading).append('\n');
        List<IndexEntry> pages = entries.stream().filter(entry -> entry.type().equals(type)).sorted(order).toList();
        if (!pages.isEmpty()) {
            index.append('\n');
        }
        pages.forEach(page -> index.append("* [").append(TextRules.escapeLinkText(page.title())).append("](/")
            .append(page.path()).append(".md) - ").append(page.description()).append('\n'));
    }
}
