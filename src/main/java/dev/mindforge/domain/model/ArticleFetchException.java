package dev.mindforge.domain.model;

/**
 * Thrown when an article URL cannot be fetched or turned into a document. The message is fixed per {@link Reason}
 * and never contains the URL or any part of the response.
 */
public class ArticleFetchException extends RuntimeException {

    /** Why the article was not fetched. */
    public enum Reason {
        POLICY("address not allowed"),
        TIMEOUT("the article did not load in time"),
        HTTP_STATUS("the server did not return the article"),
        CONTENT_TYPE("the article is not an HTML page"),
        TOO_LARGE("the article is too large"),
        NO_TITLE("the article has no title"),
        LESSON_EXISTS("a lesson for the article already exists"),
        DUPLICATE("the article is already in the knowledge base"),
        REDIRECTS("too many redirects"),
        UNREACHABLE("the server could not be reached"),
        EMPTY("the article has no text");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public ArticleFetchException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
