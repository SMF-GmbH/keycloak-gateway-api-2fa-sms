package de.smf.authenticator;

import de.smf.authenticator.api.GatewayAPIRestClient;
import de.smf.authenticator.api.SmsSender;
import de.smf.authenticator.config.FallbackRegionResolver;
import de.smf.authenticator.config.SmsConstants;
import de.smf.authenticator.config.SmsProviderConfig;
import de.smf.authenticator.otp.OtpChallengeService;
import de.smf.authenticator.phone.NormalizedPhoneNumber;
import de.smf.authenticator.phone.PhoneNumberService;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import static de.smf.authenticator.config.SmsConstants.USER_ATTRIBUTE_PHONE_NUMBER;

/**
 * Keycloak {@link Authenticator} that challenges the user with an OTP code sent via SMS.
 *
 * <p>{@link #authenticate} generates a new code and sends it to the user's configured phone
 * number; {@link #action} verifies the code submitted through the {@code login-sms.ftl} form.
 */
public class SmsAuthenticator implements Authenticator {
    private static final Logger log = LoggerFactory.getLogger(SmsAuthenticator.class);
    private static final String TEMPLATE = "login-sms.ftl";
    private static final String OTP_PREFIX = "sms_login";

    private final SmsSender smsSender;
    private final PhoneNumberService phoneNumberService;
    private final OtpChallengeService otpChallengeService;
    private final FallbackRegionResolver fallbackRegionResolver;

    public SmsAuthenticator(KeycloakSession session) {
        this(new GatewayAPIRestClient(session), new PhoneNumberService(), new OtpChallengeService());
    }

    SmsAuthenticator(SmsSender smsSender) {
        this(smsSender, new PhoneNumberService(), new OtpChallengeService());
    }

    SmsAuthenticator(SmsSender smsSender, FallbackRegionResolver fallbackRegionResolver) {
        this(smsSender, new PhoneNumberService(), new OtpChallengeService(), fallbackRegionResolver);
    }

    SmsAuthenticator(SmsSender smsSender, PhoneNumberService phoneNumberService,
                     OtpChallengeService otpChallengeService) {
        this(smsSender, phoneNumberService, otpChallengeService, new FallbackRegionResolver());
    }

    SmsAuthenticator(SmsSender smsSender, PhoneNumberService phoneNumberService,
                     OtpChallengeService otpChallengeService,
                     FallbackRegionResolver fallbackRegionResolver) {
        this.smsSender = smsSender;
        this.phoneNumberService = phoneNumberService;
        this.otpChallengeService = otpChallengeService;
        this.fallbackRegionResolver = fallbackRegionResolver;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        try {
            SmsProviderConfig config = new SmsProviderConfig(context.getAuthenticatorConfig().getConfig());
            KeycloakSession session = context.getSession();
            UserModel user = context.getUser();
            NormalizedPhoneNumber mobileNumber = phoneNumberService.normalize(
                    user.getFirstAttribute(USER_ATTRIBUTE_PHONE_NUMBER), fallbackRegion(context.getRealm()));
            OtpChallengeService.Challenge challenge =
                    otpChallengeService.createChallenge(authSession, OTP_PREFIX, config);

            Theme theme = session.theme().getTheme(Theme.Type.LOGIN);
            Locale locale = session.getContext().resolveLocale(user);
            String smsAuthText = theme.getMessages(locale).getProperty("smsAuthText");
            String smsText = String.format(smsAuthText, challenge.code(), challenge.ttlMinutes());

            if (config.isDebugMode()) {
                log.warn("SMS debug mode active, not sending SMS via gateway. OTP challenge: {}/{}",
                        user.getUsername(), challenge.code());
            } else {
                smsSender.sendViaSms(config, mobileNumber.gatewayRecipient(), smsText);
            }

            context.challenge(context.form().setAttribute("realm", context.getRealm()).createForm(TEMPLATE));
        } catch (OtpChallengeService.ResendThrottledException e) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("smsAuthResendThrottled", e.getRetryAfterSeconds())
                            .createErrorPage(Response.Status.TOO_MANY_REQUESTS));
        } catch (Exception e) {
            otpChallengeService.clearChallenge(authSession, OTP_PREFIX);
            log.error("Failed to create SMS authentication challenge for user {}",
                    context.getUser() == null ? "<unknown>" : context.getUser().getId(), e);
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("smsAuthSmsNotSent")
                            .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String enteredCode = context.getHttpRequest().getDecodedFormParameters().getFirst(SmsConstants.AUTHNOTE_CODE);

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        SmsProviderConfig config;
        try {
            config = new SmsProviderConfig(context.getAuthenticatorConfig().getConfig());
        } catch (Exception e) {
            log.error("Invalid SMS authenticator configuration", e);
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }

        OtpChallengeService.VerificationResult result =
                otpChallengeService.verify(authSession, OTP_PREFIX, enteredCode, config);

        switch (result.status()) {
            case VALID -> context.success();
            case EXPIRED -> context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                    context.form().setError("smsAuthCodeExpired").createErrorPage(Response.Status.BAD_REQUEST));
            case TOO_MANY_ATTEMPTS -> context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form().setError("smsAuthTooManyAttempts").createErrorPage(Response.Status.BAD_REQUEST));
            case MISSING -> context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            case INVALID -> context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form().setAttribute("realm", context.getRealm())
                            .setError("smsAuthCodeInvalid").createForm(TEMPLATE));
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        try {
            phoneNumberService.normalize(user.getFirstAttribute(USER_ATTRIBUTE_PHONE_NUMBER), fallbackRegion(realm));
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("User {} has invalid phone number: {}", user.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Both this check and {@link #authenticate} must resolve the region the same way, otherwise a
     * user could pass {@code configuredFor} and then have the code sent to the wrong country.
     */
    private String fallbackRegion(RealmModel realm) {
        return fallbackRegionResolver.resolve(realm == null ? null : realm.getName());
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        return;
    }

    @Override
    public void close() {
    }
}
