package de.smf.authenticator.api;

import de.smf.authenticator.api.dto.MobileMessageRequest;
import de.smf.authenticator.config.SmsProviderConfig;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.models.KeycloakSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * {@link SmsSender} implementation that delivers messages through the GatewayAPI REST API.
 *
 * @see <a href="https://gatewayapi.com/docs/message/overview/">GatewayAPI message API docs</a>
 */
public class GatewayAPIRestClient implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(GatewayAPIRestClient.class);

    private final KeycloakSession session;

    public GatewayAPIRestClient(KeycloakSession session) {
        this.session = session;
    }

    /// See https://gatewayapi.com/docs/message/overview/
    @Override
    public void sendViaSms(SmsProviderConfig config, String recipient, String smsText) {
        MobileMessageRequest body = new MobileMessageRequest(
                config.getSenderId(),
                recipient,
                smsText
        );
        try (SimpleHttpResponse resp = SimpleHttp.create(session)
                .doPost(config.getApiUrl() + "/mobile/single")
                .header("Authorization", "Token " + config.getApiToken())
                .json(body)
                .asResponse()) {
            if (resp.getStatus() != 202) {
                log.error("sendViaSms({}): gateway returned HTTP {} - {}",
                        maskRecipient(recipient), resp.getStatus(), sanitizeGatewayBody(resp.asString()));
                throw new RuntimeException("Failed to send SMS");
            }
        } catch (IOException e) {
            throw new RuntimeException("SMS gateway request failed", e);
        }
    }

    private static String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        return "***" + recipient.substring(recipient.length() - 4);
    }

    private static String sanitizeGatewayBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.length() > 256 ? body.substring(0, 256) : body;
    }
}
