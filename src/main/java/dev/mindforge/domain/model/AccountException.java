package dev.mindforge.domain.model;

/** Thrown when an account cannot be created or signed in to. */
public class AccountException extends SecurityException {

    private final boolean conflict;

    private AccountException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public static AccountException emailTaken() {
        return new AccountException("An account with this email already exists", true);
    }

    /** The same message for an unknown email and a wrong password, so neither can be probed. */
    public static AccountException invalidCredentials() {
        return new AccountException("Invalid email or password", false);
    }

    /** True for a registration conflict, false for a failed sign-in. */
    public boolean conflict() {
        return conflict;
    }
}
