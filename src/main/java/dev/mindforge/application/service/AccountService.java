package dev.mindforge.application.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import dev.mindforge.domain.model.AccountException;
import dev.mindforge.domain.model.NotFoundException;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.PasswordHasher;
import dev.mindforge.domain.port.UserRepository;

/** Registration, password sign-in, identity-provider sign-in and the profile. Emails are stored lowercased. */
public class AccountService {

    private final UserRepository users;
    private final PasswordHasher hasher;

    public AccountService(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    /** @throws AccountException when the email already has an account */
    public User register(String displayName, String email, String password) {
        String normalised = normalise(email);
        if (users.findByEmail(normalised).isPresent()) {
            throw AccountException.emailTaken();
        }
        return users.insert(new User(UUID.randomUUID(), TextRules.singleLine(displayName), normalised,
            hasher.hash(password), null, null, null));
    }

    /** @throws AccountException for an unknown email, an account without a password, or a wrong password */
    public User authenticate(String email, String password) {
        // ponytail: an unknown email skips the hash comparison, so timing can reveal it; Phase 20 hardens sign-in
        User user = users.findByEmail(normalise(email))
            .filter(candidate -> candidate.passwordHash() != null && hasher.matches(password, candidate.passwordHash()))
            .orElseThrow(AccountException::invalidCredentials);
        users.recordLogin(user.userId(), Instant.now());
        return user;
    }

    /**
     * Signs in with an email an identity provider verified, creating the account on first sign-in. An existing
     * account with that email is the same person, so it is signed in rather than duplicated.
     */
    public User signInWithProvider(String email, String displayName, String avatarUrl) {
        String normalised = normalise(email);
        User user = users.findByEmail(normalised).orElseGet(() -> users.insert(new User(UUID.randomUUID(),
            TextRules.singleLine(displayName == null || displayName.isBlank() ? normalised : displayName),
            normalised, null, avatarUrl, null, null)));
        users.recordLogin(user.userId(), Instant.now());
        return user;
    }

    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }

    public User rename(UUID userId, String displayName) {
        return users.updateDisplayName(userId, TextRules.singleLine(displayName));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
