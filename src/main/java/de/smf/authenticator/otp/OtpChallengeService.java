package de.smf.authenticator.otp;

import de.smf.authenticator.config.SmsProviderConfig;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;

/**
 * Creates and verifies SMS OTP challenges, storing their state as auth notes on the current
 * {@link AuthenticationSessionModel}.
 *
 * <p>Codes are salted and SHA-256 hashed before storage, and compared in constant time on
 * verification. Also enforces code expiry, a maximum number of verification attempts, and a
 * cooldown between resend requests.
 */
public class OtpChallengeService {
    private static final String NOTE_HASH = "_hash";
    private static final String NOTE_SALT = "_salt";
    private static final String NOTE_EXPIRES_AT = "_expires_at";
    private static final String NOTE_ATTEMPTS = "_attempts";
    private static final String NOTE_LAST_SENT_AT = "_last_sent_at";

    private final Clock clock;

    public OtpChallengeService() {
        this(Clock.systemUTC());
    }

    OtpChallengeService(Clock clock) {
        this.clock = clock;
    }

    public Challenge createChallenge(AuthenticationSessionModel authSession, String prefix, SmsProviderConfig config) {
        long now = clock.millis();
        long lastSentAt = longNote(authSession, prefix + NOTE_LAST_SENT_AT, 0L);
        long nextAllowedAt = lastSentAt + (config.getResendCooldownSeconds() * 1000L);
        if (hasActiveChallenge(authSession, prefix) && now < nextAllowedAt) {
            throw new ResendThrottledException(Math.max(1L, (nextAllowedAt - now + 999L) / 1000L));
        }

        String code = SecretGenerator.getInstance().randomString(config.getCodeLength(), SecretGenerator.DIGITS);
        String salt = SecretGenerator.getInstance().randomString(24);
        long expiresAt = now + (config.getCodeTtl() * 1000L);

        authSession.setAuthNote(prefix + NOTE_HASH, hash(salt, code));
        authSession.setAuthNote(prefix + NOTE_SALT, salt);
        authSession.setAuthNote(prefix + NOTE_EXPIRES_AT, Long.toString(expiresAt));
        authSession.setAuthNote(prefix + NOTE_ATTEMPTS, "0");
        authSession.setAuthNote(prefix + NOTE_LAST_SENT_AT, Long.toString(now));

        return new Challenge(code, Math.floorDiv(config.getCodeTtl(), 60));
    }

    public VerificationResult verify(AuthenticationSessionModel authSession, String prefix, String enteredCode,
                                     SmsProviderConfig config) {
        String expectedHash = authSession.getAuthNote(prefix + NOTE_HASH);
        String salt = authSession.getAuthNote(prefix + NOTE_SALT);
        long expiresAt = longNote(authSession, prefix + NOTE_EXPIRES_AT, 0L);

        if (expectedHash == null || salt == null || expiresAt == 0L) {
            return new VerificationResult(VerificationStatus.MISSING);
        }
        if (expiresAt < clock.millis()) {
            clearChallenge(authSession, prefix);
            return new VerificationResult(VerificationStatus.EXPIRED);
        }
        if (enteredCode == null || !constantTimeEquals(expectedHash, hash(salt, enteredCode))) {
            int attempts = intNote(authSession, prefix + NOTE_ATTEMPTS, 0) + 1;
            authSession.setAuthNote(prefix + NOTE_ATTEMPTS, Integer.toString(attempts));
            if (attempts >= config.getMaxAttempts()) {
                clearChallenge(authSession, prefix);
                return new VerificationResult(VerificationStatus.TOO_MANY_ATTEMPTS);
            }
            return new VerificationResult(VerificationStatus.INVALID);
        }

        clearChallenge(authSession, prefix);
        return new VerificationResult(VerificationStatus.VALID);
    }

    public void clearChallenge(AuthenticationSessionModel authSession, String prefix) {
        authSession.removeAuthNote(prefix + NOTE_HASH);
        authSession.removeAuthNote(prefix + NOTE_SALT);
        authSession.removeAuthNote(prefix + NOTE_EXPIRES_AT);
        authSession.removeAuthNote(prefix + NOTE_ATTEMPTS);
        authSession.removeAuthNote(prefix + NOTE_LAST_SENT_AT);
    }

    private boolean hasActiveChallenge(AuthenticationSessionModel authSession, String prefix) {
        return authSession.getAuthNote(prefix + NOTE_HASH) != null;
    }

    private static int intNote(AuthenticationSessionModel authSession, String key, int defaultValue) {
        String value = authSession.getAuthNote(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longNote(AuthenticationSessionModel authSession, String key, long defaultValue) {
        String value = authSession.getAuthNote(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String salt, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * A freshly issued OTP challenge.
     *
     * @param code       the plaintext code to send to the user
     * @param ttlMinutes how long, in minutes, the code remains valid
     */
    public record Challenge(String code, long ttlMinutes) {
    }

    /**
     * The outcome of verifying a submitted code, see {@link VerificationStatus}.
     */
    public record VerificationResult(VerificationStatus status) {
    }

    /**
     * Possible outcomes of an OTP verification attempt.
     */
    public enum VerificationStatus {
        /** The submitted code matched and the challenge has been cleared. */
        VALID,
        /** The submitted code did not match; attempts remain. */
        INVALID,
        /** The challenge existed but its TTL has elapsed. */
        EXPIRED,
        /** The maximum number of invalid attempts has been reached; the challenge has been cleared. */
        TOO_MANY_ATTEMPTS,
        /** No active challenge was found for this session. */
        MISSING
    }

    /**
     * Thrown when a new challenge is requested before the resend cooldown has elapsed.
     */
    public static class ResendThrottledException extends RuntimeException {
        private final long retryAfterSeconds;

        public ResendThrottledException(long retryAfterSeconds) {
            super("SMS resend is throttled");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
