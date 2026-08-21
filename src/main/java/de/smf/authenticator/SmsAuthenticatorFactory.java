package de.smf.authenticator;

import de.smf.authenticator.config.SmsConstants;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * {@link AuthenticatorFactory} for {@link SmsAuthenticator}, registered under the
 * {@value #PROVIDER_ID} provider ID and exposing its admin-console configuration properties.
 */
public class SmsAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "sms-authenticator";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "SMS Authentication";
    }

    @Override
    public String getHelpText() {
        return "Validates an OTP sent via SMS to the users mobile phone.";
    }

    @Override
    public String getReferenceCategory() {
        return "otp";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of(
                new ProviderConfigProperty(SmsConstants.CONFIG_API_URL, "API URL", "URL under which the SMS Gateway is reachable.", ProviderConfigProperty.STRING_TYPE, "https://messaging.gatewayapi.eu"),
                new ProviderConfigProperty(SmsConstants.CONFIG_API_TOKEN, "API Token", "Token for authentication with SMS Gateway.", ProviderConfigProperty.PASSWORD, "", true),
                new ProviderConfigProperty(SmsConstants.CONFIG_CODE_LENGTH, "Code length", "The number of digits of the generated code.", ProviderConfigProperty.STRING_TYPE, "8"),
                new ProviderConfigProperty(SmsConstants.CONFIG_CODE_TTL, "Time-to-live (seconds)", "The time to live in seconds for the code to be valid.", ProviderConfigProperty.STRING_TYPE, "250"),
                new ProviderConfigProperty(SmsConstants.CONFIG_SENDER_ID, "Sender ID", "The sender ID is displayed as the message sender on the receiving device.", ProviderConfigProperty.STRING_TYPE, "SMF GmbH"),
                new ProviderConfigProperty(SmsConstants.CONFIG_MAX_ATTEMPTS, "Maximum attempts", "Maximum number of invalid OTP submissions before the challenge is consumed.", ProviderConfigProperty.STRING_TYPE, "5"),
                new ProviderConfigProperty(SmsConstants.CONFIG_RESEND_COOLDOWN, "Resend cooldown (seconds)", "Minimum wait time before another SMS can be sent.", ProviderConfigProperty.STRING_TYPE, "20"),
                new ProviderConfigProperty(SmsConstants.CONFIG_DEBUG_MODE, "Debug mode", "If enabled, no SMS is sent via the gateway. Instead the OTP is written to the Keycloak server log as <username>/<otp>. Do not enable in production.", ProviderConfigProperty.BOOLEAN_TYPE, false)
        );
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new SmsAuthenticator(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
