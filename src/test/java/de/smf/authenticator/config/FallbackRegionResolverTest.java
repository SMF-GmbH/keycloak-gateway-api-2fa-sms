package de.smf.authenticator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FallbackRegionResolverTest {

    @Test
    void noVariablesSet_returnsNull() {
        assertNull(resolver(Map.of()).resolve("master"));
    }

    @Test
    void globalVariable_isUsedWhenNoRealmOverrideExists() {
        assertEquals("AT", resolver(Map.of("SMS_FALLBACK_REGION", "AT")).resolve("master"));
    }

    @Test
    void realmOverride_winsOverGlobalVariable() {
        var env = Map.of(
                "SMS_FALLBACK_REGION", "DE",
                "SMS_FALLBACK_REGION_master", "AT");

        assertEquals("AT", resolver(env).resolve("master"));
    }

    @Test
    void realmOverride_appliesOnlyToItsOwnRealm() {
        var env = Map.of(
                "SMS_FALLBACK_REGION", "DE",
                "SMS_FALLBACK_REGION_acme", "AT");

        assertEquals("AT", resolver(env).resolve("acme"));
        assertEquals("DE", resolver(env).resolve("other"));
    }

    @Test
    void realmNameIsMatchedVerbatim_includingCase() {
        var env = Map.of("SMS_FALLBACK_REGION_MyRealm", "NL");

        assertEquals("NL", resolver(env).resolve("MyRealm"));
        assertNull(resolver(env).resolve("myrealm"));
    }

    /** Realm names may contain characters a POSIX shell cannot use in a variable name. */
    @ParameterizedTest
    @ValueSource(strings = {"acme-prod", "acme.prod", "acme prod"})
    void realmNameWithShellUnsafeCharacters_isMatchedByNormalizedName(String realmName) {
        var env = Map.of("SMS_FALLBACK_REGION_ACME_PROD", "NL");

        assertEquals("NL", resolver(env).resolve(realmName));
    }

    @Test
    void verbatimName_winsOverNormalizedName() {
        var env = Map.of(
                "SMS_FALLBACK_REGION_acme-prod", "AT",
                "SMS_FALLBACK_REGION_ACME_PROD", "NL");

        assertEquals("AT", resolver(env).resolve("acme-prod"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void missingRealmName_fallsBackToGlobalVariable(String realmName) {
        assertEquals("DE", resolver(Map.of("SMS_FALLBACK_REGION", "DE")).resolve(realmName));
    }

    @Test
    void blankRealmOverride_fallsBackToGlobalVariable() {
        var env = Map.of(
                "SMS_FALLBACK_REGION", "DE",
                "SMS_FALLBACK_REGION_master", "   ");

        assertEquals("DE", resolver(env).resolve("master"));
    }

    private FallbackRegionResolver resolver(Map<String, String> environment) {
        return new FallbackRegionResolver(environment::get);
    }
}
