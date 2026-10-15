package dev.mindforge.domain.model;

/**
 * The channel a document arrived through. A {@code CONVERSATION} document is a chat turn, never deduplicated and
 * outside the lesson rule; an {@code ARTICLE} is fetched from a reference and its run may only create pages.
 */
public enum UploadSource {
    API,
    FILE_WATCHER,
    CONVERSATION,
    ARTICLE
}
