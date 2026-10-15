package dev.mindforge.application.service;

import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.IndexEntry;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.port.PageSearchQuery;

/** Page search for the page browser, over the lexical query the index prefilter will share. */
public class SearchService {

    private static final int LIMIT = 20;
    private static final int MIN_QUERY_LENGTH = 2;

    private final PageSearchQuery pages;

    public SearchService(PageSearchQuery pages) {
        this.pages = pages;
    }

    public List<IndexEntry> search(UUID kbId, String query) {
        String normalised = TextRules.singleLine(query == null ? "" : query);
        return normalised.length() < MIN_QUERY_LENGTH ? List.of() : pages.search(kbId, normalised, LIMIT);
    }
}
