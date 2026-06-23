package dev.mindforge.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Internal SHA-256 helper shared by the domain value objects that derive
// deterministic identifiers. JDK-only; no I/O, no framework.
final class Hashes {

    private static final String SHA_256 = "SHA-256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hashes() {}

    static String sha256Hex(byte[] raw) {
        try {
            return toHex(MessageDigest.getInstance(SHA_256).digest(raw));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    static String sha256Hex(String raw) {
        return sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }
}
