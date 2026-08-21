package de.smf.authenticator;

import de.smf.authenticator.config.SmsConstants;
import org.junit.jupiter.api.Test;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsAuthenticatorFactoryTest {

    private final SmsAuthenticatorFactory factory = new SmsAuthenticatorFactory();

    @Test
    void getId_returnsProviderId() {
        assertEquals("sms-authenticator", factory.getId());
    }

    @Test
    void isConfigurable_returnsTrue() {
        assertTrue(factory.isConfigurable());
    }

    @Test
    void isUserSetupAllowed_returnsFalse() {
        assertFalse(factory.isUserSetupAllowed());
    }

    @Test
    void getRequirementChoices_offersAllChoices() {
        assertEquals(
                List.of(AuthenticationExecutionModel.Requirement.REQUIRED,
                        AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                        AuthenticationExecutionModel.Requirement.DISABLED),
                List.of(factory.getRequirementChoices()));
    }

    @Test
    void getConfigProperties_containsAllExpectedKeysExactlyOnce() {
        List<ProviderConfigProperty> properties = factory.getConfigProperties();
        List<String> names = properties.stream().map(ProviderConfigProperty::getName).collect(Collectors.toList());

        assertEquals(Set.copyOf(names).size(), names.size());
        assertTrue(names.containsAll(List.of(
                SmsConstants.CONFIG_API_URL,
                SmsConstants.CONFIG_API_TOKEN,
                SmsConstants.CONFIG_CODE_LENGTH,
                SmsConstants.CONFIG_CODE_TTL,
                SmsConstants.CONFIG_SENDER_ID,
                SmsConstants.CONFIG_MAX_ATTEMPTS,
                SmsConstants.CONFIG_RESEND_COOLDOWN,
                SmsConstants.CONFIG_DEBUG_MODE)));
    }

    @Test
    void getConfigProperties_apiTokenIsMarkedSecret() {
        ProviderConfigProperty apiToken = findProperty(SmsConstants.CONFIG_API_TOKEN);

        assertTrue(apiToken.isSecret());
        assertEquals(ProviderConfigProperty.PASSWORD, apiToken.getType());
    }

    @Test
    void getConfigProperties_debugModeIsBooleanAndDefaultsToFalse() {
        ProviderConfigProperty debugMode = findProperty(SmsConstants.CONFIG_DEBUG_MODE);

        assertEquals(ProviderConfigProperty.BOOLEAN_TYPE, debugMode.getType());
        assertEquals(false, debugMode.getDefaultValue());
        assertFalse(debugMode.isSecret());
    }

    private ProviderConfigProperty findProperty(String name) {
        return factory.getConfigProperties().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing config property: " + name));
    }
}
