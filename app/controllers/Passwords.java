package controllers;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Password hashing, password policy and the one-time tokens for setting a password. */
public final class Passwords {
    public static final int MIN_LENGTH = 10;
    // BCrypt silently ignores everything past 72 bytes; rejecting longer input keeps
    // two passwords that only differ after that point from matching each other.
    static final int MAX_BYTES = 72;
    private static final int COST = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() { }

    static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(COST, RANDOM));
    }

    static boolean verify(String password, String hash) {
        if (password == null || hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Burns the same time as a real check. Called for unknown, inactive or passwordless
     * nicks so the response time does not reveal which nicks exist.
     */
    static void verifyDummy(String password) {
        BCrypt.checkpw(password == null ? "" : password, DummyHash.VALUE);
    }

    /** Returns an error message, or null if the password is acceptable. */
    static String validate(String password, String confirmation, String nick) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "The password must be at least " + MIN_LENGTH + " characters long.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "The password must not be longer than " + MAX_BYTES + " bytes.";
        }
        if (nick != null && password.equalsIgnoreCase(nick)) {
            return "The password must not be your nick.";
        }
        if (!password.equals(confirmation)) {
            return "The two passwords do not match.";
        }
        return null;
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String tokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static final class DummyHash {
        static final String VALUE = BCrypt.hashpw(newToken(), BCrypt.gensalt(COST, RANDOM));
    }
}
