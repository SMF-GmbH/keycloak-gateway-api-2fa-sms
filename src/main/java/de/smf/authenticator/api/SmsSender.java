package de.smf.authenticator.api;

import de.smf.authenticator.config.SmsProviderConfig;

/**
 * Abstraction for delivering an SMS message to a recipient, allowing the SMS gateway
 * implementation to be swapped out (e.g. for testing or an alternative provider).
 */
public interface SmsSender {
    void sendViaSms(SmsProviderConfig config, long recipient, String smsText);
}
