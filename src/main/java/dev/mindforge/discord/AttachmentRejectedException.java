package dev.mindforge.discord;

/** Thrown when a Discord attachment is not read: it is not on Discord's CDN, too large, or the download failed. */
public class AttachmentRejectedException extends RuntimeException {

    public enum Reason { NOT_FROM_DISCORD, TOO_LARGE, DOWNLOAD_FAILED }

    private final Reason reason;

    public AttachmentRejectedException(Reason reason) {
        super("Attachment rejected: " + reason);
        this.reason = reason;
    }

    public AttachmentRejectedException(Reason reason, Throwable cause) {
        super("Attachment rejected: " + reason, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
