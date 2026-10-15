package dev.mindforge.domain.model;

/** Thrown when a revert or a supersession removal is not offered. */
public class RevertNotAllowedException extends IllegalStateException {

    public RevertNotAllowedException(String reason) {
        super(reason);
    }
}
