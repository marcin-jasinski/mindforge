package dev.mindforge.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.mindforge.domain.port.PasswordHasher;

/** BCrypt at cost 12, the floor {@code web-security.md} sets. */
public class BCryptPasswordHasher implements PasswordHasher {

    private static final int COST = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
