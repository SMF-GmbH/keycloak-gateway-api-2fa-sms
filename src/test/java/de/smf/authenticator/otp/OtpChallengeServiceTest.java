package de.smf.authenticator.otp;

import de.smf.authenticator.config.SmsConstants;
import de.smf.authenticator.config.SmsProviderConfig;
import org.junit.jupiter.api.Test;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OtpChallengeServiceTest {

    private static final String PREFIX = "sms_login";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final OtpChallengeService service = new OtpChallengeService(clock);
    private final AuthenticationSessionModel authSession = mapBackedAuthSession();

    @Test
    void createChallenge_generatesCodeMatchingConfiguredLength() {
        SmsProviderConfig config = config(Map.of(SmsConstants.CONFIG_CODE_LENGTH, "8"));

        OtpChallengeService.Challenge challenge = service.createChallenge(authSession, PREFIX, config);

        assertEquals(8, challenge.code().length());
        assertTrue(challenge.code().chars().allMatch(Character::isDigit));
    }

    @Test
    void createChallenge_ttlMinutesIsFloorDivOfTtlSeconds() {
        SmsProviderConfig config = config(Map.of(SmsConstants.CONFIG_CODE_TTL, "125"));

        OtpChallengeService.Challenge challenge = service.createChallenge(authSession, PREFIX, config);

        assertEquals(2, challenge.ttlMinutes());
    }

    @Test
    void createChallenge_secondCallWithinCooldown_throwsResendThrottled() {
        SmsProviderConfig config = config(Map.of(SmsConstants.CONFIG_RESEND_COOLDOWN, "30"));
        service.createChallenge(authSession, PREFIX, config);

        clock.advance(10_000);

        OtpChallengeService.ResendThrottledException ex = assertThrows(
                OtpChallengeService.ResendThrottledException.class,
                () -> service.createChallenge(authSession, PREFIX, config));
        assertEquals(20, ex.getRetryAfterSeconds());
    }

    @Test
    void createChallenge_afterCooldownElapsed_succeeds() {
        SmsProviderConfig config = config(Map.of(SmsConstants.CONFIG_RESEND_COOLDOWN, "30"));
        service.createChallenge(authSession, PREFIX, config);

        clock.advance(30_000);

        assertDoesNotThrow(() -> service.createChallenge(authSession, PREFIX, config));
    }

    @Test
    void verify_correctCode_returnsValidAndClearsChallenge() {
        SmsProviderConfig config = config(Map.of());
        String code = service.createChallenge(authSession, PREFIX, config).code();

        OtpChallengeService.VerificationResult result = service.verify(authSession, PREFIX, code, config);

        assertEquals(OtpChallengeService.VerificationStatus.VALID, result.status());
        assertNull(authSession.getAuthNote(PREFIX + "_hash"));
    }

    @Test
    void verify_noChallengeCreated_returnsMissing() {
        SmsProviderConfig config = config(Map.of());

        OtpChallengeService.VerificationResult result = service.verify(authSession, PREFIX, "123456", config);

        assertEquals(OtpChallengeService.VerificationStatus.MISSING, result.status());
    }

    @Test
    void verify_expiredChallenge_returnsExpiredAndClearsChallenge() {
        SmsProviderConfig config = config(Map.of(SmsConstants.CONFIG_CODE_TTL, "60"));
        String code = service.createChallenge(authSession, PREFIX, config).code();

        clock.advance(61_000);

        OtpChallengeService.VerificationResult result = service.verify(authSession, PREFIX, code, config);

        assertEquals(OtpChallengeService.VerificationStatus.EXPIRED, result.status());
        assertNull(authSession.getAuthNote(PREFIX + "_hash"));
    }

    @Test
    void verify_wrongCode_returnsInvalidAndIncrementsAttempts() {
        SmsProviderConfig config = config(Map.of(SmsConstants.CONFIG_MAX_ATTEMPTS, "5"));
        service.createChallenge(authSession, PREFIX, config);

        OtpChallengeService.VerificationResult result = service.verify(authSession, PREFIX, "000000", config);

        assertEquals(OtpChallengeService.VerificationStatus.INVALID, result.status());
        assertEquals("1", authSession.getAuthNote(PREFIX + "_attempts"));
    }

    @Test
    void verify_nullEnteredCode_returnsInvalid() {
        SmsProviderConfig config = config(Map.of());
        service.createChallenge(authSession, PREFIX, config);

        OtpChallengeService.VerificationResult result = service.verify(authSession, PREFIX, null, config);

        assertEquals(OtpChallengeService.VerificationStatus.INVALID, result.status());
    }

    @Test
    void verify_reachingMaxAttempts_returnsTooManyAttemptsAndClearsChallenge() {
        SmsProviderConfig config = config(Map.of(SmsConstants.CONFIG_MAX_ATTEMPTS, "2"));
        service.createChallenge(authSession, PREFIX, config);

        service.verify(authSession, PREFIX, "000000", config);
        OtpChallengeService.VerificationResult result = service.verify(authSession, PREFIX, "000000", config);

        assertEquals(OtpChallengeService.VerificationStatus.TOO_MANY_ATTEMPTS, result.status());
        assertNull(authSession.getAuthNote(PREFIX + "_hash"));
    }

    @Test
    void clearChallenge_removesAllNotes() {
        SmsProviderConfig config = config(Map.of());
        service.createChallenge(authSession, PREFIX, config);

        service.clearChallenge(authSession, PREFIX);

        assertNull(authSession.getAuthNote(PREFIX + "_hash"));
        assertNull(authSession.getAuthNote(PREFIX + "_salt"));
        assertNull(authSession.getAuthNote(PREFIX + "_expires_at"));
        assertNull(authSession.getAuthNote(PREFIX + "_attempts"));
        assertNull(authSession.getAuthNote(PREFIX + "_last_sent_at"));
    }

    private SmsProviderConfig config(Map<String, String> overrides) {
        Map<String, String> config = new HashMap<>();
        config.put(SmsConstants.CONFIG_API_URL, "https://sms.test");
        config.put(SmsConstants.CONFIG_API_TOKEN, "token");
        config.put(SmsConstants.CONFIG_CODE_LENGTH, "6");
        config.put(SmsConstants.CONFIG_CODE_TTL, "300");
        config.put(SmsConstants.CONFIG_SENDER_ID, "Test");
        config.put(SmsConstants.CONFIG_MAX_ATTEMPTS, "5");
        config.put(SmsConstants.CONFIG_RESEND_COOLDOWN, "0");
        config.putAll(overrides);
        return new SmsProviderConfig(config);
    }

    private AuthenticationSessionModel mapBackedAuthSession() {
        AuthenticationSessionModel session = mock(AuthenticationSessionModel.class);
        Map<String, String> notes = new HashMap<>();
        when(session.getAuthNote(anyString())).thenAnswer(invocation -> notes.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            notes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(session).setAuthNote(anyString(), anyString());
        doAnswer(invocation -> {
            notes.remove(invocation.getArgument(0));
            return null;
        }).when(session).removeAuthNote(anyString());
        return session;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
