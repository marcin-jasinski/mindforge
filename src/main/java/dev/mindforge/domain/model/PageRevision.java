package dev.mindforge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable snapshot of a page immediately after one write, stamped with its run. A {@code null} body is a
 * tombstone. The path is recorded too, because a deleted page has no live row to take it from.
 */
public record PageRevision(
    UUID pageId,
    int revision,
    UUID ingestRunId,
    String path,
    String title,
    String description,
    PageType type,
    String markdownBody,
    Instant createdAt
) {

    public boolean isTombstone() {
        return markdownBody == null;
    }

    /** The write that brings the page back to this revision's content. */
    public PageWrite toWrite() {
        return new PageWrite(pageId, path, title, description, type, markdownBody);
    }
}
