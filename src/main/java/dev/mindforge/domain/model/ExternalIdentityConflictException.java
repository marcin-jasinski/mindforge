package dev.mindforge.domain.model;

/**
 * Thrown when a user and its external identity link cannot be created because the identity is already linked or the
 * user's email is already taken. Nothing was written.
 */
public class ExternalIdentityConflictException extends IllegalStateException {

    public ExternalIdentityConflictException(ExternalPlatform platform) {
        super("A " + platform + " identity is already linked, or its account email is taken");
    }
}
