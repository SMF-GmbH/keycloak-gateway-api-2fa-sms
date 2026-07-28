package de.smf.authenticator.config;

/**
 * Central registry of the configuration keys and authentication note names used by the
 * SMS authenticator SPI.
 */
public final class SmsConstants {
    public static final String AUTHNOTE_CODE = "code";
    public static final String CONFIG_CODE_LENGTH = "length";
    public static final String CONFIG_CODE_TTL = "ttl";
    public static final String CONFIG_API_URL = "apiUrl";
    public static final String CONFIG_API_TOKEN = "apiToken";
    public static final String CONFIG_SENDER_ID = "senderId";
    public static final String CONFIG_DEFAULT_REGION = "defaultRegion";
    public static final String CONFIG_MAX_ATTEMPTS = "maxAttempts";
    public static final String CONFIG_RESEND_COOLDOWN = "resendCooldown";
    public static final String CONFIG_DEBUG_MODE = "debugMode";

    private SmsConstants() {
    }
}
