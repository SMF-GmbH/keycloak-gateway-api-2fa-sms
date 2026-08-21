package de.smf.authenticator.phone;

/**
 * Thrown when a fallback region is configured but is not a region libphonenumber knows.
 *
 * <p>Deliberately not an {@link IllegalArgumentException}: that is how this package reports an
 * unusable phone <em>number</em>, and callers translate it into "this user cannot receive an SMS"
 * and skip the step. A broken <em>configuration</em> must not be downgraded to that — a typo in
 * the region would then silently switch off two-factor authentication for everyone. It has to
 * fail the flow instead.
 */
public class InvalidFallbackRegionException extends RuntimeException {

    public InvalidFallbackRegionException(String region) {
        super("configured SMS fallback region '" + region
                + "' is not a known ISO 3166-1 alpha-2 region");
    }
}
