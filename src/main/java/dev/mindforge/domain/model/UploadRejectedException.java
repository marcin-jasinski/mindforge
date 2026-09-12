package dev.mindforge.domain.model;

/** Thrown when an upload may not be ingested: its type, size or filename is refused, or it cannot be read. */
public class UploadRejectedException extends IllegalArgumentException {

    public UploadRejectedException(String message) {
        super(message);
    }

    public UploadRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
