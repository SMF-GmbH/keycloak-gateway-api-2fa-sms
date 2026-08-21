package de.smf.authenticator.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Locale;

/**
 * Parses and validates raw phone number strings using {@link PhoneNumberUtil}, normalizing
 * them to a {@link NormalizedPhoneNumber}.
 *
 * <p>Numbers that carry their own country code in E.164 notation (leading {@code +}) are parsed
 * without any region hint; national notations such as {@code 0151 12345678} are interpreted
 * against the fallback region passed by the caller.
 */
public class PhoneNumberService {
    /**
     * Region assumed for numbers without a country code when no valid fallback is configured.
     */
    public static final String DEFAULT_FALLBACK_REGION = "DE";

    private final PhoneNumberUtil phoneNumberUtil;

    public PhoneNumberService() {
        this(PhoneNumberUtil.getInstance());
    }

    PhoneNumberService(PhoneNumberUtil phoneNumberUtil) {
        this.phoneNumberUtil = phoneNumberUtil;
    }

    /**
     * @param raw            the number as stored on the user
     * @param fallbackRegion ISO 3166-1 alpha-2 region applied only to numbers without a country
     *                       code; {@code null} and blank mean "not configured" and select
     *                       {@link #DEFAULT_FALLBACK_REGION}
     * @throws InvalidFallbackRegionException if a region is configured but unknown
     * @throws IllegalArgumentException       if {@code raw} is not a usable phone number
     */
    public NormalizedPhoneNumber normalize(String raw, String fallbackRegion) {
        String resolvedFallback = resolveFallbackRegion(fallbackRegion);

        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("phone number must not be blank");
        }

        String value = raw.trim();
        String region = value.startsWith("+") ? null : resolvedFallback;

        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(value, region);
            if (!phoneNumberUtil.isValidNumber(parsed)) {
                throw new IllegalArgumentException("phone number is not valid");
            }
            String e164 = phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            try {
                return new NormalizedPhoneNumber(e164, Long.parseLong(e164.substring(1)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("phone number could not be converted to a gateway recipient", e);
            }
        } catch (NumberParseException e) {
            throw new IllegalArgumentException("phone number could not be parsed", e);
        }
    }

    /**
     * Called before {@code raw} is looked at on purpose: a broken configuration must surface even
     * when the number in hand would not have needed the fallback at all.
     */
    private String resolveFallbackRegion(String fallbackRegion) {
        if (fallbackRegion == null || fallbackRegion.isBlank()) {
            return DEFAULT_FALLBACK_REGION;
        }
        String region = fallbackRegion.trim().toUpperCase(Locale.ROOT);
        if (!phoneNumberUtil.getSupportedRegions().contains(region)) {
            throw new InvalidFallbackRegionException(fallbackRegion);
        }
        return region;
    }
}
