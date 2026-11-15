package dev.mindforge.slack;

/** Thrown when a Slack file is not read: it is not on Slack's file host, too large, or the download failed. */
public class SlackFileRejectedException extends RuntimeException {

    public enum Reason { NOT_FROM_SLACK, TOO_LARGE, DOWNLOAD_FAILED }

    private final Reason reason;

    public SlackFileRejectedException(Reason reason) {
        super("Slack file rejected: " + reason);
        this.reason = reason;
    }

    public SlackFileRejectedException(Reason reason, Throwable cause) {
        super("Slack file rejected: " + reason, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
