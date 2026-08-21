package de.smf.authenticator.config;

/**
 * Central registry of the configuration keys and authentication note names used by the
 * SMS authenticator SPI.
 */
public final class SmsConstants {
    /**
     * Environment variable holding the ISO 3166-1 alpha-2 region used for phone numbers stored
     * without a country code, optionally suffixed with a realm name. Read from the environment
     * rather than the execution config because it must also be available to
     * {@code configuredFor}, which is not handed an authenticator config.
     *
     * @see FallbackRegionResolver
     */
    public static final String ENV_FALLBACK_REGION = "SMS_FALLBACK_REGION";

    public static final String AUTHNOTE_CODE = "code";
    public static final String CONFIG_CODE_LENGTH = "length";
    public static final String CONFIG_CODE_TTL = "ttl";
    public static final String CONFIG_API_URL = "apiUrl";
    public static final String CONFIG_API_TOKEN = "apiToken";
    public static final String CONFIG_SENDER_ID = "senderId";
    public static final String CONFIG_MAX_ATTEMPTS = "maxAttempts";
    public static final String CONFIG_RESEND_COOLDOWN = "resendCooldown";
    public static final String CONFIG_DEBUG_MODE = "debugMode";

    private SmsConstants() {
    }
}
