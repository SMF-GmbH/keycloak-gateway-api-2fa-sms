package de.smf.authenticator.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Validated, typed view over the SMS authenticator's execution configuration.
 *
 * <p>Parses the raw config {@link Map} supplied by the authentication execution (as set on
 * the execution in the Keycloak admin console), applying defaults and range checks, and
 * rejecting invalid values such as a non-HTTPS gateway URL.
 */
public class SmsProviderConfig {
    private static final String DEFAULT_API_URL = "https://messaging.gatewayapi.eu";
    private static final int DEFAULT_CODE_LENGTH = 6;
    private static final int DEFAULT_CODE_TTL = 300;
    private static final String DEFAULT_SENDER_ID = "SMF GmbH";
    public static final String DEFAULT_REGION = "DE";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RESEND_COOLDOWN = 60;

    private final Map<String, String> config;
    private final String apiUrl;
    private final String apiToken;
    private final int codeLength;
    private final int codeTtl;
    private final String senderId;
    private final String defaultRegion;
    private final int maxAttempts;
    private final int resendCooldownSeconds;
    private final boolean debugMode;

    public SmsProviderConfig(Map<String, String> config) {
        this.config = config;
        this.apiUrl = validateHttpsUrl(valueOrDefault(SmsConstants.CONFIG_API_URL, DEFAULT_API_URL));
        this.apiToken = require(SmsConstants.CONFIG_API_TOKEN);
        this.codeLength = intValue(SmsConstants.CONFIG_CODE_LENGTH, DEFAULT_CODE_LENGTH, 6, 10);
        this.codeTtl = intValue(SmsConstants.CONFIG_CODE_TTL, DEFAULT_CODE_TTL, 60, 600);
        this.senderId = valueOrDefault(SmsConstants.CONFIG_SENDER_ID, DEFAULT_SENDER_ID);
        this.defaultRegion = valueOrDefault(SmsConstants.CONFIG_DEFAULT_REGION, DEFAULT_REGION).toUpperCase();
        this.maxAttempts = intValue(SmsConstants.CONFIG_MAX_ATTEMPTS, DEFAULT_MAX_ATTEMPTS, 1, 10);
        this.resendCooldownSeconds = intValue(SmsConstants.CONFIG_RESEND_COOLDOWN, DEFAULT_RESEND_COOLDOWN, 0, 600);
        this.debugMode = booleanValue(SmsConstants.CONFIG_DEBUG_MODE, false);
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public int getCodeTtl() {
        return codeTtl;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private String require(String key) {
        String value = config == null ? null : config.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing SMS authenticator config: " + key);
        }
        return value.trim();
    }

    private String valueOrDefault(String key, String defaultValue) {
        String value = config == null ? null : config.get(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private int intValue(String key, int defaultValue, int min, int max) {
        String value = config == null ? null : config.get(key);
        int parsed;
        if (value == null || value.isBlank()) {
            parsed = defaultValue;
        } else {
            try {
                parsed = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid SMS authenticator config: " + key, e);
            }
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(
                    "Invalid SMS authenticator config: " + key + " must be between " + min + " and " + max);
        }
        return parsed;
    }

    private boolean booleanValue(String key, boolean defaultValue) {
        String value = config == null ? null : config.get(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private static String validateHttpsUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("SMS API URL must be an HTTPS URL");
            }
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("SMS API URL is invalid", e);
        }
    }

}
