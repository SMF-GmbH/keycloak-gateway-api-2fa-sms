package de.smf.authenticator.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

/**
 * Parses and validates raw phone number strings using {@link PhoneNumberUtil}, normalizing
 * them to a {@link NormalizedPhoneNumber}.
 */
public class PhoneNumberService {
    private final PhoneNumberUtil phoneNumberUtil;

    public PhoneNumberService() {
        this(PhoneNumberUtil.getInstance());
    }

    PhoneNumberService(PhoneNumberUtil phoneNumberUtil) {
        this.phoneNumberUtil = phoneNumberUtil;
    }

    public NormalizedPhoneNumber normalize(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("phone number must not be blank");
        }

        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(raw.trim(), defaultRegion);
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
}
