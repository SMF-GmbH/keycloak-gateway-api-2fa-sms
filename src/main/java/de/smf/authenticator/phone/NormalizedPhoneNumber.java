package de.smf.authenticator.phone;

/**
 * A phone number that has been parsed and validated by {@link PhoneNumberService}.
 *
 * @param e164             the number in E.164 format (e.g. {@code +491701234567})
 * @param gatewayRecipient the number as expected by the GatewayAPI recipient field (E.164 without the leading {@code +})
 */
public record NormalizedPhoneNumber(String e164, String gatewayRecipient) {
}
