package de.smf.authenticator.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsProviderConfigTest {

    @Test
    void missingApiToken_throws() {
        Map<String, String> config = baseConfig();
        config.remove(SmsConstants.CONFIG_API_TOKEN);

        assertThrows(IllegalArgumentException.class, () -> new SmsProviderConfig(config));
    }

    @Test
    void nonHttpsApiUrl_throws() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_API_URL, "http://sms.test");

        assertThrows(IllegalArgumentException.class, () -> new SmsProviderConfig(config));
    }

    @Test
    void apiUrlWithTrailingSlash_isStripped() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_API_URL, "https://sms.test/");

        SmsProviderConfig result = new SmsProviderConfig(config);

        assertEquals("https://sms.test", result.getApiUrl());
    }

    @Test
    void missingApiUrl_fallsBackToDefault() {
        Map<String, String> config = baseConfig();
        config.remove(SmsConstants.CONFIG_API_URL);

        SmsProviderConfig result = new SmsProviderConfig(config);

        assertEquals("https://messaging.gatewayapi.eu", result.getApiUrl());
    }

    @Test
    void codeLengthBelowMinimum_throws() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_CODE_LENGTH, "5");

        assertThrows(IllegalArgumentException.class, () -> new SmsProviderConfig(config));
    }

    @Test
    void codeLengthAboveMaximum_throws() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_CODE_LENGTH, "11");

        assertThrows(IllegalArgumentException.class, () -> new SmsProviderConfig(config));
    }

    @Test
    void codeLengthNotANumber_throws() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_CODE_LENGTH, "abc");

        assertThrows(IllegalArgumentException.class, () -> new SmsProviderConfig(config));
    }

    @Test
    void maxAttemptsBelowMinimum_throws() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_MAX_ATTEMPTS, "0");

        assertThrows(IllegalArgumentException.class, () -> new SmsProviderConfig(config));
    }

    @Test
    void resendCooldownAboveMaximum_throws() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_RESEND_COOLDOWN, "601");

        assertThrows(IllegalArgumentException.class, () -> new SmsProviderConfig(config));
    }

    @Test
    void defaultRegion_isUppercased() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_DEFAULT_REGION, "de");

        SmsProviderConfig result = new SmsProviderConfig(config);

        assertEquals("DE", result.getDefaultRegion());
    }

    @Test
    void debugMode_defaultsToFalseWhenAbsent() {
        Map<String, String> config = baseConfig();

        SmsProviderConfig result = new SmsProviderConfig(config);

        assertFalse(result.isDebugMode());
    }

    @Test
    void debugMode_true_isParsed() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_DEBUG_MODE, "true");

        SmsProviderConfig result = new SmsProviderConfig(config);

        assertTrue(result.isDebugMode());
    }

    @Test
    void debugMode_blank_fallsBackToDefault() {
        Map<String, String> config = baseConfig();
        config.put(SmsConstants.CONFIG_DEBUG_MODE, "  ");

        SmsProviderConfig result = new SmsProviderConfig(config);

        assertFalse(result.isDebugMode());
    }

    private Map<String, String> baseConfig() {
        Map<String, String> config = new HashMap<>();
        config.put(SmsConstants.CONFIG_API_URL, "https://sms.test");
        config.put(SmsConstants.CONFIG_API_TOKEN, "token");
        config.put(SmsConstants.CONFIG_CODE_LENGTH, "6");
        config.put(SmsConstants.CONFIG_CODE_TTL, "300");
        config.put(SmsConstants.CONFIG_SENDER_ID, "Test");
        config.put(SmsConstants.CONFIG_DEFAULT_REGION, "DE");
        config.put(SmsConstants.CONFIG_MAX_ATTEMPTS, "5");
        config.put(SmsConstants.CONFIG_RESEND_COOLDOWN, "0");
        return config;
    }
}
