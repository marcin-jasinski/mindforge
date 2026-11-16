package dev.mindforge.application.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import dev.mindforge.domain.model.AccountException;
import dev.mindforge.domain.model.ExternalAccounts;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.PasswordHasher;
import dev.mindforge.domain.port.UserRepository;

/** Registration, password sign-in, identity-provider sign-in and the profile. Emails are stored lowercased. */
public class AccountService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    /** Compared against when there is no stored hash, so every sign-in attempt costs one hash comparison. */
    private final String dummyHash;

    public AccountService(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
        this.dummyHash = hasher.hash(UUID.randomUUID().toString());
    }

    /** @throws AccountException when the email already has an account or is in the reserved domain */
    public User register(String displayName, String email, String password) {
        String normalised = unreserved(normalise(email));
        if (users.findByEmail(normalised).isPresent()) {
            throw AccountException.emailTaken();
        }
        User created = users.insert(new User(UUID.randomUUID(), TextRules.singleLine(displayName), normalised,
            hasher.hash(password), null, null, null));
        // registering signs the person in, so it is their first login
        Instant now = Instant.now();
        users.recordLogin(created.userId(), now);
        return users.findById(created.userId()).orElse(created);
    }

    /** @throws AccountException for an unknown email, an account without a password, or a wrong password */
    public User authenticate(String email, String password) {
        Optional<User> found = users.findByEmail(normalise(email));
        String hash = found.map(User::passwordHash).orElse(null);
        boolean matches = hasher.matches(password, hash != null ? hash : dummyHash);
        if (found.isEmpty() || hash == null || !matches) {
            throw AccountException.invalidCredentials();
        }
        User user = found.get();
        users.recordLogin(user.userId(), Instant.now());
        return user;
    }

    /**
     * Signs in with an email an identity provider verified, creating the account on first sign-in. An existing
     * account with that email is the same person, so it is signed in rather than duplicated.
     *
     * @throws AccountException when the email is in the reserved domain
     */
    public User signInWithProvider(String email, String displayName, String avatarUrl) {
        String normalised = unreserved(normalise(email));
        User user = users.findByEmail(normalised).orElseGet(() -> users.insert(new User(UUID.randomUUID(),
            TextRules.singleLine(displayName == null || displayName.isBlank() ? normalised : displayName),
            normalised, null, avatarUrl, null, null)));
        users.recordLogin(user.userId(), Instant.now());
        return user;
    }

    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }

    /**
     * Resolves the user an email names, normalised as at sign-in, without signing them in.
     *
     * @throws NotFoundException when no account has that email
     */
    public User byEmail(String email) {
        return users.findByEmail(normalise(email)).orElseThrow(() -> new NotFoundException("User"));
    }

    public User rename(UUID userId, String displayName) {
        return users.updateDisplayName(userId, TextRules.singleLine(displayName));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String unreserved(String normalisedEmail) {
        if (ExternalAccounts.isReserved(normalisedEmail)) {
            throw AccountException.reservedEmail();
        }
        return normalisedEmail;
    }
}
