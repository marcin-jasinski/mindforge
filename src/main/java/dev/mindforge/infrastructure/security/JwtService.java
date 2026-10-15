package dev.mindforge.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.ResponseCookie;

/**
 * Issues and verifies the session token, an HS256 JWT whose subject is the user id, carried only in an
 * {@code HttpOnly; SameSite=Lax} cookie — never in a response body. The secret must be at least 32 bytes.
 */
public class JwtService {

    public static final String COOKIE = "token";

    private final SecretKey key;
    private final Duration expiry;
    private final boolean secureCookies;

    public JwtService(String secret, Duration expiry, boolean secureCookies) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = expiry;
        this.secureCookies = secureCookies;
    }

    /** The user a token was issued to, if it is signed by this server and unexpired. */
    public Optional<UUID> verify(String token) {
        try {
            return Optional.of(UUID.fromString(
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public ResponseCookie sessionCookie(UUID userId) {
        Instant now = Instant.now();
        String token = Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiry)))
            .signWith(key)
            .compact();
        return cookie(token, expiry);
    }

    public ResponseCookie clearedCookie() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(secureCookies)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build();
    }
}
