package de.smf.authenticator.config;

import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Resolves the region used for phone numbers stored without a country code, from the environment
 * of the Keycloak server. The variable names and their precedence are documented under "Fallback
 * region" in {@code README.md}.
 *
 * <p>Returns {@code null} when nothing is set and does not validate what it finds:
 * {@code PhoneNumberService.normalize} applies the default and rejects an unknown region, so
 * neither decision is made in two places.
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
