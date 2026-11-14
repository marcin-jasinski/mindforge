package dev.mindforge.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The email of an account a bot provisions for an external identity (ADR 0027). Such an address is in the reserved,
 * non-deliverable domain {@code users.invalid}, so no person can register or sign in with one.
 */
public final class ExternalAccounts {

    public static final String RESERVED_EMAIL_DOMAIN = "users.invalid";

    private static final Pattern EXTERNAL_ID = Pattern.compile("[A-Za-z0-9]+");
    private static final int MAX_EMAIL_LENGTH = 255;

    private ExternalAccounts() {
    }

    /**
     * Returns {@code <platform>-<externalId>@users.invalid}, lowercased.
     *
     * @throws IllegalArgumentException for a blank id, an id outside {@code [A-Za-z0-9]}, or a result over 255 chars
     */
    public static String syntheticEmail(ExternalPlatform platform, String externalId) {
        if (externalId == null || !EXTERNAL_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("An external id must be letters and digits only");
        }
        String email = platform.name().toLowerCase(Locale.ROOT) + "-" + externalId.toLowerCase(Locale.ROOT) + "@"
            + RESERVED_EMAIL_DOMAIN;
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("An external id is too long for an account email");
        }
        return email;
    }

    /** True when a normalised (trimmed, lowercased) email is in the reserved domain. */
    public static boolean isReserved(String normalisedEmail) {
        return normalisedEmail.endsWith("@" + RESERVED_EMAIL_DOMAIN);
    }
}
