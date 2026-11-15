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
    private static final Pattern SLACK_ID = Pattern.compile("[A-Za-z0-9]+-[A-Za-z0-9]+");
    private static final int MAX_EMAIL_LENGTH = 255;

    private ExternalAccounts() {
    }

    /**
     * Returns the Slack external id {@code <home team id>-<user id>} (ADR 0033), with the case of each part kept.
     *
     * @throws IllegalArgumentException for a null or blank part, or a part outside {@code [A-Za-z0-9]}
     */
    public static String slackExternalId(String teamId, String userId) {
        if (teamId == null || !EXTERNAL_ID.matcher(teamId).matches()
            || userId == null || !EXTERNAL_ID.matcher(userId).matches()) {
            throw new IllegalArgumentException("A Slack team id and user id must be letters and digits only");
        }
        return teamId + "-" + userId;
    }

    /**
     * Returns {@code <platform>-<externalId>@users.invalid}, lowercased. A Slack id is
     * {@code <home team id>-<user id>}; any other platform's id is letters and digits only.
     *
     * @throws IllegalArgumentException for a blank id, an id that does not have its platform's form, or a result
     *     over 255 chars
     */
    public static String syntheticEmail(ExternalPlatform platform, String externalId) {
        Pattern form = platform == ExternalPlatform.SLACK ? SLACK_ID : EXTERNAL_ID;
        if (externalId == null || !form.matcher(externalId).matches()) {
            throw new IllegalArgumentException(platform == ExternalPlatform.SLACK
                ? "A Slack external id must be <team id>-<user id> in letters and digits"
                : "An external id must be letters and digits only");
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
