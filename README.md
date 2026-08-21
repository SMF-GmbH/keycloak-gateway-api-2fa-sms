# Keycloak GatewayAPI SMS Authenticator

A [Keycloak](https://www.keycloak.org/) Authentication SPI that adds SMS-based
one-time-password (OTP) two-factor authentication to a login flow, sending
codes through the [GatewayAPI](https://gatewayapi.com/docs/message/overview/)
SMS gateway.

## Features

- Sends a numeric OTP code to the user's `phoneNumber` attribute via GatewayAPI's REST API.
- Configurable code length, time-to-live, maximum attempts, and resend cooldown.
- OTP codes are salted, SHA-256 hashed, and compared in constant time — never stored or logged in plaintext.
- Phone numbers are validated with [libphonenumber](https://github.com/google/libphonenumber) and normalized to E.164, using a fixed fallback region for numbers without a country code.
- Enforces an HTTPS-only gateway URL.
- Optional debug mode for local testing without sending real SMS messages.
- Localized login page and messages (English, German, and French included).

## How it works

1. During authentication, `SmsAuthenticator` reads the authenticated user's `phoneNumber` attribute and normalizes it to E.164.
2. A random OTP code of the configured `length` is generated and its salted hash is stored as an auth note on the authentication session, along with an expiry timestamp, an attempt counter, and the send timestamp used for resend throttling.
3. The code is sent to the user via GatewayAPI's `/mobile/single` endpoint, using the message template `smsAuthText`.
4. The user is presented with the `login-sms.ftl` form to enter the received code.
5. On submission, the entered code is hashed and compared against the stored hash. The flow succeeds on a match; otherwise the attempt counter is incremented, and the challenge is invalidated after too many failed attempts or on expiry.

## Requirements

- Java 21
- Maven 3.9+
- Keycloak 26.7.1 (server-side dependencies are `provided` scope)

## Building

```bash
mvn clean package
```

This produces a shaded JAR at `target/de.smf-SmsAuthenticator.jar` (the `libphonenumber` dependency is relocated/shaded to avoid classpath conflicts with other Keycloak extensions).

## Installation

1. Copy the built JAR into your Keycloak server's `providers/` directory.
2. Rebuild the Keycloak provider cache:
   ```bash
   kc.sh build
   ```
3. Restart Keycloak.
4. In the Admin Console, go to **Authentication**, duplicate or edit a browser flow, and add the **SMS Authentication** execution step (category: OTP).
5. Click the gear icon next to the step to configure it (see below).
6. Set the step's requirement to **Required** (or **Alternative**, if it sits next to other second-factor options).

Users must have a valid phone number set in the `phoneNumber` attribute of their account for this step to apply (`configuredFor` returns `false`, and the step is skipped, if the attribute is missing or fails phone number validation).

Numbers carrying their own country code (`+4915112345678`, `+49 151 12345678`) are used as given. Numbers without one (`0151 12345678`, `004915112345678`) are interpreted against the realm's fallback region — see below.

## Configuration

These options are set on the authentication execution's config in the Admin Console:

| Setting | Config key | Default | Description |
|---|---|---|---|
| API URL | `apiUrl` | `https://messaging.gatewayapi.eu` | Base URL of the SMS gateway. Must use HTTPS. |
| API Token | `apiToken` | *(required)* | Token used to authenticate with the SMS gateway, sent as `Authorization: Token <apiToken>`. |
| Code length | `length` | `8` | Number of digits in the generated OTP (6–10). |
| Time-to-live (seconds) | `ttl` | `250` | How long a generated code remains valid (60–600). |
| Sender ID | `senderId` | `SMF GmbH` | Sender name shown on the recipient's device. |
| Maximum attempts | `maxAttempts` | `5` | Number of invalid submissions allowed before the code is invalidated (1–10). |
| Resend cooldown (seconds) | `resendCooldown` | `20` | Minimum wait time before another SMS may be sent (0–600). |
| Debug mode | `debugMode` | `false` | If enabled, no SMS is sent — the OTP is written to the server log as `<username>/<otp>` instead. **Do not enable in production.** |

### Fallback region

Phone numbers stored without a country code are interpreted against an ISO 3166-1 alpha-2 region taken from the Keycloak server's environment. Numbers that already start with `+` ignore it entirely.

The first variable that is set and non-blank wins:

| Variable | Scope |
|---|---|
| `SMS_FALLBACK_REGION_<realmName>` | The named realm, matched verbatim including case — e.g. `SMS_FALLBACK_REGION_master`. |
| `SMS_FALLBACK_REGION_<REALM_NAME>` | The named realm, uppercased with every character outside `A–Z`/`0–9` replaced by `_` — e.g. `SMS_FALLBACK_REGION_ACME_PROD` for the realm `acme-prod`. Use this form for realm names a shell cannot put in a variable name. |
| `SMS_FALLBACK_REGION` | All realms on the server. |

If none is set, or the value is blank, the region defaults to `DE`. A value that *is* set but is not a region libphonenumber knows (`XX`, `germany`, `DEU`) is treated as a misconfiguration: the SMS step fails with an internal error and logs `InvalidFallbackRegionException`. It is validated on every login, so a typo shows up immediately.

Failing is deliberate here. Falling back to `DE` on a typo would send codes to the wrong country, and answering "this user has no usable number" would make Keycloak *skip* the SMS step. Lower-case values (`at`) are fine; only unknown regions fail.

```yaml
services:
  keycloak:
    environment:
      SMS_FALLBACK_REGION: DE          # every other realm
      SMS_FALLBACK_REGION_ACME_AT: AT  # realm "acme-at"
```

Changing a value needs a server restart. If a single realm serves users across several countries, store their numbers in E.164 (`+43…`) instead of relying on this fallback.

## Customizing messages

User-facing text lives in `src/main/resources/theme-resources/messages/messages_*.properties` and the login form in `src/main/resources/theme-resources/templates/login-sms.ftl`. Add additional `messages_<locale>.properties` files to support more languages, and package the theme resources into your Keycloak theme to customize the look and feel.

The `smsAuthText` key is the SMS body itself and is formatted with `String.format`: `%1$s` is the OTP code and `%2$d` the validity in whole minutes.

## Testing

```bash
mvn test
```

Unit tests cover configuration validation, OTP challenge lifecycle (creation, verification, expiry, attempt limits, resend throttling), the authenticator's challenge and verification flow, and the authenticator factory.

## Local development

A Docker Compose setup builds the JAR and runs it in Keycloak against PostgreSQL:

```bash
docker compose up --build
```

- Admin Console: <http://localhost:8080> (`admin` / `admin`)
- Remote JVM debugging is enabled on port `5005`.
- `de.smf.authenticator` logs at `debug` level.

## Security-Advertise

App-based one-time passwords (TOTP) and passkeys offer significantly stronger protection than SMS-based two-factor authentication.

The core problem with SMS is that the security of your account depends on your mobile carrier. In a SIM-swapping attack, criminals use social engineering to convince a carrier to transfer your phone number to a device they control — and with it, every code sent to that number. Text messages are also transmitted unencrypted and can be intercepted through known weaknesses in mobile signalling protocols such as SS7. Because the codes arrive over a network you do not control, you have no way to detect or prevent this.

## Support & Links

- [Keycloak Security Scanner](https://www.smf.de/keycloak-scanner/) – test your configuration for
  vulnerabilities
- [Keycloak Consulting](https://www.smf.de/keycloak-beratung/) – from production setup to integration
- [Keycloak – Official Documentation](https://www.keycloak.org/documentation)

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
