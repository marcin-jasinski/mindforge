package dev.mindforge.domain.port;

/** One-way password hashing; the raw password is never stored or logged. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
