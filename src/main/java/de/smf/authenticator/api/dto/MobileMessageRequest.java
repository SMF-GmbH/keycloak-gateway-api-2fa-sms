package de.smf.authenticator.api.dto;

/**
 * Request payload for GatewayAPI's {@code /mobile/single} endpoint.
 *
 * @param sender    the sender ID displayed on the recipient's device
 * @param recipient the recipient's phone number in the gateway's expected format (E.164 digits, no leading {@code +})
 * @param message   the SMS message body
 */
public record MobileMessageRequest(String sender, long recipient, String message) {
}
