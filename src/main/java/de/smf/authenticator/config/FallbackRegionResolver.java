package de.smf.authenticator.config;

import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Resolves the region used for phone numbers stored without a country code, from the environment
 * of the Keycloak server.
 *
 * <p>Lookup order, first non-blank value wins:
 * <ol>
 *   <li>{@code SMS_FALLBACK_REGION_<realmName>} — the realm name verbatim, e.g.
 *       {@code SMS_FALLBACK_REGION_master}</li>
 *   <li>{@code SMS_FALLBACK_REGION_<REALM_NAME>} — the realm name uppercased with every character
 *       outside {@code [A-Za-z0-9]} replaced by {@code _}, e.g. {@code SMS_FALLBACK_REGION_ACME_PROD}
 *       for the realm {@code acme-prod}. Realm names may contain characters that a POSIX shell
 *       cannot put in a variable name, so the verbatim form alone is not always settable.</li>
 *   <li>{@code SMS_FALLBACK_REGION} — the server-wide default</li>
 * </ol>
 *
 * <p>Returns {@code null} when nothing is set; {@code PhoneNumberService.normalize} then applies
 * its own default. Values are not validated here, only where the region is used — a set but
 * unknown region is a misconfiguration and fails the login there rather than being silently
 * replaced by the default.
 */
public class FallbackRegionResolver {
    private final UnaryOperator<String> environment;

    public FallbackRegionResolver() {
        this(System::getenv);
    }

    /**
     * @param environment lookup of environment variables by name, normally {@code System::getenv}
     */
    public FallbackRegionResolver(UnaryOperator<String> environment) {
        this.environment = environment;
    }

    /**
     * @param realmName name of the realm being authenticated against, may be {@code null}
     */
    public String resolve(String realmName) {
        if (realmName != null && !realmName.isBlank()) {
            String perRealm = firstNonBlank(
                    lookup(SmsConstants.ENV_FALLBACK_REGION + "_" + realmName),
                    lookup(SmsConstants.ENV_FALLBACK_REGION + "_" + normalize(realmName)));
            if (perRealm != null) {
                return perRealm;
            }
        }
        return lookup(SmsConstants.ENV_FALLBACK_REGION);
    }

    private String lookup(String name) {
        String value = environment.apply(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private static String normalize(String realmName) {
        return realmName.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
    }
}
