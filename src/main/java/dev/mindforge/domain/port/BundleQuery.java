package dev.mindforge.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.mindforge.domain.model.SourceCitation;

/**
 * What a rendered page projects besides its body: the documents behind its {@code # Citations}. Used by export and the
 * page view; it reads no study table and no run internals (T11, T25).
 */
public interface BundleQuery {

    /** Every document behind these pages, oldest first; one row per page and document. */
    List<SourceCitation> citations(UUID kbId, Collection<UUID> pageIds);
}
