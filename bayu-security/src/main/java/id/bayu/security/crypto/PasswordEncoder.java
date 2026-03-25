package id.bayu.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Simple password hashing using SHA-256 + salt.
 * Not as strong as BCrypt but zero external dependencies.
 */
public class PasswordEncoder {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_LENGTH = 16;

    public String encode(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] hash = hash(rawPassword, salt);
        // Format: base64(salt):base64(hash)
        return Base64.getEncoder().encodeToString(salt) + ":" +
                Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String rawPassword, String encoded) {
        if (encoded == null || !encoded.contains(":")) return false;
        String[] parts = encoded.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] actualHash = hash(rawPassword, salt);
        return MessageDigest.isEqual(expectedHash, actualHash);
    }

    private byte[] hash(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            // Multiple rounds for slight hardening
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 10000; i++) {
                md.reset();
                md.update(salt);
                hash = md.digest(hash);
            }
            return hash;
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }
}
