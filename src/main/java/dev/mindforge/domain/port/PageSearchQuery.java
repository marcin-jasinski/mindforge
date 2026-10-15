package dev.mindforge.domain.port;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.IndexEntry;

/**
 * Lexical page search: title and description substrings and {@code simple} full-text over bodies. The same code the
 * index prefilter will use past the retrieval ceiling (ADR 0016).
 */
public interface PageSearchQuery {

    /** Title matches first. */
    List<IndexEntry> search(UUID kbId, String query, int limit);
}
