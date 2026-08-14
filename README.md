# Keycloak GatewayAPI SMS Authenticator

A [Keycloak](https://www.keycloak.org/) Authentication SPI that adds SMS-based
one-time-password (OTP) two-factor authentication to a login flow, sending
codes through the [GatewayAPI](https://gatewayapi.com/docs/message/overview/)
SMS gateway.

## Features

- Sends a numeric OTP code to the user's `phoneNumber` attribute via GatewayAPI's REST API.
- Configurable code length, time-to-live, maximum attempts, and resend cooldown.
- OTP codes are salted, SHA-256 hashed, and compared in constant time — never stored or logged in plaintext.
- Phone numbers are parsed and validated with [libphonenumber](https://github.com/google/libphonenumber) and normalized to E.164.
- Enforces an HTTPS-only gateway URL.
- Optional debug mode for local testing without sending real SMS messages.
- Localized login page and messages (English, German, and French included).

## How it works

1. During authentication, `SmsAuthenticator` reads the authenticated user's `phoneNumber` attribute and normalizes it against the configured default region.
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

## Configuration

These options are set on the authentication execution's config in the Admin Console:

| Setting | Config key | Default | Description |
|---|---|---|---|
| API URL | `apiUrl` | `https://messaging.gatewayapi.eu` | Base URL of the SMS gateway. Must use HTTPS. |
| API Token | `apiToken` | *(required)* | Token used to authenticate with the SMS gateway, sent as `Authorization: Token <apiToken>`. |
| Code length | `length` | `8` | Number of digits in the generated OTP (6–10). |
| Time-to-live (seconds) | `ttl` | `250` | How long a generated code remains valid (60–600). |
| Sender ID | `senderId` | `SMF GmbH` | Sender name shown on the recipient's device. |
| Default phone region | `defaultRegion` | `DE` | ISO 3166-1 alpha-2 region used to parse national (non-`+`) phone numbers. |
| Maximum attempts | `maxAttempts` | `5` | Number of invalid submissions allowed before the code is invalidated (1–10). |
| Resend cooldown (seconds) | `resendCooldown` | `20` | Minimum wait time before another SMS may be sent (0–600). |
| Debug mode | `debugMode` | `false` | If enabled, no SMS is sent — the OTP is written to the server log as `<username>/<otp>` instead. **Do not enable in production.** |

## Customizing messages

User-facing text lives in `src/main/resources/theme-resources/messages/messages_*.properties` and the login form in `src/main/resources/theme-resources/templates/login-sms.ftl`. Add additional `messages_<locale>.properties` files to support more languages, and package the theme resources into your Keycloak theme to customize the look and feel.

## Testing

```bash
mvn test
```

Unit tests cover configuration validation, OTP challenge lifecycle (creation, verification, expiry, attempt limits, resend throttling), the authenticator's challenge and verification flow, and the authenticator factory.


## Support & Links

- [Keycloak Security Scanner](https://www.smf.de/keycloak-scanner/) – test your configuration for
  vulnerabilities
- [Keycloak Consulting](https://www.smf.de/keycloak-beratung/) – from production setup to integration
- [Keycloak – Official Documentation](https://www.keycloak.org/documentation)

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
