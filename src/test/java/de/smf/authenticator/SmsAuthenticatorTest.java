package de.smf.authenticator;

import de.smf.authenticator.api.SmsSender;
import de.smf.authenticator.config.SmsConstants;
import de.smf.authenticator.config.SmsProviderConfig;
import de.smf.authenticator.otp.OtpChallengeService;
import de.smf.authenticator.phone.PhoneNumberService;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.ThemeManager;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.theme.Theme;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.keycloak.representations.IDToken.PHONE_NUMBER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsAuthenticatorTest {

    private static final String PHONE_NUMBER_RAW = "+49 151 12345678";
    private static final String PHONE_NUMBER_GATEWAY = "4915112345678";
    private static final String OTP_PREFIX = "sms_login";

    private SmsSender smsSender;
    private SmsAuthenticator authenticator;

    private AuthenticationFlowContext context;
    private AuthenticationSessionModel authSession;
    private UserModel user;
    private LoginFormsProvider form;

    @BeforeEach
    void setUp() throws Exception {
        smsSender = mock(SmsSender.class);
        authenticator = new SmsAuthenticator(smsSender);

        context = mock(AuthenticationFlowContext.class);
        authSession = mapBackedAuthSession();
        user = mock(UserModel.class);
        form = mock(LoginFormsProvider.class);

        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(context.getUser()).thenReturn(user);
        when(context.form()).thenReturn(form);
        when(form.setAttribute(anyString(), any())).thenReturn(form);
        when(form.setError(anyString(), any())).thenReturn(form);
        when(form.setError(anyString())).thenReturn(form);
        when(form.createForm(anyString())).thenReturn(mock(Response.class));
        when(form.createErrorPage(any())).thenReturn(mock(Response.class));
    }

    @Test
    void authenticate_success_sendsSmsAndIssuesChallenge() throws Exception {
        stubAuthenticateHappyPath();

        authenticator.authenticate(context);

        verify(smsSender).sendViaSms(any(), eq(PHONE_NUMBER_GATEWAY), anyString());
        verify(form).createForm("login-sms.ftl");
        verify(context).challenge(any());
        verify(context, never()).failureChallenge(any(), any());
    }

    @Test
    void authenticate_debugMode_logsOtpInsteadOfSendingSms() throws Exception {
        stubAuthenticateHappyPath(Map.of(SmsConstants.CONFIG_DEBUG_MODE, "true"));
        when(user.getUsername()).thenReturn("jdoe");

        authenticator.authenticate(context);

        verify(smsSender, never()).sendViaSms(any(), anyString(), anyString());
        verify(form).createForm("login-sms.ftl");
        verify(context).challenge(any());
        verify(context, never()).failureChallenge(any(), any());
    }

    @Test
    void authenticate_clientThrows_failsWithInternalError() throws Exception {
        stubAuthenticateHappyPath();
        doThrow(new RuntimeException("gateway down"))
                .when(smsSender).sendViaSms(any(), anyString(), anyString());

        authenticator.authenticate(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.INTERNAL_ERROR), any());
        verify(context, never()).challenge(any());
    }

    @Test
    void action_validCode_callsSuccess() {
        String code = seedOtpChallenge();
        stubActionContext(code);

        authenticator.action(context);

        verify(context).success();
        verify(context, never()).failureChallenge(any(), any());
    }

    @Test
    void action_expiredCode_failsWithExpiredCode() {
        String code = seedOtpChallenge(Map.of(SmsConstants.CONFIG_CODE_TTL, "60"));
        authSession.setAuthNote(OTP_PREFIX + "_expires_at", Long.toString(System.currentTimeMillis() - 1_000L));
        stubActionContext(code);

        authenticator.action(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.EXPIRED_CODE), any());
        verify(context, never()).success();
    }

    @Test
    void action_invalidCode_alwaysFailsCredentials() {
        seedOtpChallenge();
        stubActionContext("999999");

        authenticator.action(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any());
        verify(context, never()).attempted();
        verify(context, never()).success();
    }

    @Test
    void action_tooManyInvalidAttempts_consumesChallenge() {
        seedOtpChallenge(Map.of(SmsConstants.CONFIG_MAX_ATTEMPTS, "1"));
        stubActionContext("999999");

        authenticator.action(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any());
        verify(form).setError("smsAuthTooManyAttempts");
    }

    @Test
    void action_missingChallenge_failsWithInternalError() {
        stubActionContext("123456");
        stubConfig();

        authenticator.action(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.INTERNAL_ERROR), any());
        verify(context, never()).success();
    }

    @Test
    void requiresUser_returnsTrue() {
        assertTrue(authenticator.requiresUser());
    }

    @Test
    void configuredFor_userHasPhoneNumber_returnsTrue() {
        when(user.getFirstAttribute(PHONE_NUMBER)).thenReturn(PHONE_NUMBER_RAW);
        assertTrue(authenticator.configuredFor(mock(KeycloakSession.class), mock(RealmModel.class), user));
    }

    @Test
    void configuredFor_userMissingPhoneNumber_returnsFalse() {
        when(user.getFirstAttribute(PHONE_NUMBER)).thenReturn(null);
        assertFalse(authenticator.configuredFor(mock(KeycloakSession.class), mock(RealmModel.class), user));
    }

    @Test
    void configuredFor_userHasInvalidPhoneNumber_returnsFalse() {
        when(user.getFirstAttribute(PHONE_NUMBER)).thenReturn("not-a-number");
        assertFalse(authenticator.configuredFor(mock(KeycloakSession.class), mock(RealmModel.class), user));
    }

    @ParameterizedTest
    @CsvSource({
            "'+4915112345678',    '+4915112345678', '4915112345678'",
            "'+49 151 12345678',  '+4915112345678', '4915112345678'",
            "'015112345678',      '+4915112345678', '4915112345678'",
            "'0151 12345678',     '+4915112345678', '4915112345678'",
            "'+1 415 555 2671',   '+14155552671',   '14155552671'"
    })
    void phoneNumberService_normalizesToE164AndGatewayRecipient(String input, String e164, String gatewayRecipient) {
        var normalized = new PhoneNumberService().normalize(input, "DE");

        assertEquals(e164, normalized.e164());
        assertEquals(gatewayRecipient, normalized.gatewayRecipient());
    }

    @Test
    void phoneNumberService_nonDigitInput_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhoneNumberService().normalize("abc", "DE"));
    }

    @Test
    void phoneNumberService_emptyString_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhoneNumberService().normalize("", "DE"));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void phoneNumberService_null_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhoneNumberService().normalize(null, "DE"));
    }

    private void stubAuthenticateHappyPath() throws Exception {
        stubAuthenticateHappyPath(Map.of());
    }

    private void stubAuthenticateHappyPath(Map<String, String> configOverrides) throws Exception {
        stubConfig(configOverrides);

        var session = mock(KeycloakSession.class);
        var keycloakCtx = mock(KeycloakContext.class);
        var themeManager = mock(ThemeManager.class);
        var theme = mock(Theme.class);
        var messages = new Properties();
        messages.setProperty("smsAuthText", "Your code is %s, valid for %d minutes");

        when(context.getSession()).thenReturn(session);
        when(session.getContext()).thenReturn(keycloakCtx);
        when(session.theme()).thenReturn(themeManager);
        when(themeManager.getTheme(Theme.Type.LOGIN)).thenReturn(theme);
        when(theme.getMessages(any(Locale.class))).thenReturn(messages);
        when(keycloakCtx.resolveLocale(user)).thenReturn(Locale.ENGLISH);

        when(user.getFirstAttribute(PHONE_NUMBER)).thenReturn(PHONE_NUMBER_RAW);
        when(context.getRealm()).thenReturn(mock(RealmModel.class));
    }

    @SuppressWarnings("unchecked")
    private void stubActionContext(String enteredCode) {
        var http = mock(HttpRequest.class);
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.getFirst(SmsConstants.AUTHNOTE_CODE)).thenReturn(enteredCode);
        when(http.getDecodedFormParameters()).thenReturn(params);
        when(context.getHttpRequest()).thenReturn(http);
    }

    private String seedOtpChallenge() {
        return seedOtpChallenge(Map.of());
    }

    private String seedOtpChallenge(Map<String, String> overrides) {
        stubConfig(overrides);
        return new OtpChallengeService()
                .createChallenge(authSession, OTP_PREFIX, new SmsProviderConfig(configMap(overrides)))
                .code();
    }

    private void stubConfig() {
        stubConfig(Map.of());
    }

    private void stubConfig(Map<String, String> overrides) {
        var configModel = mock(AuthenticatorConfigModel.class);
        when(configModel.getConfig()).thenReturn(configMap(overrides));
        when(context.getAuthenticatorConfig()).thenReturn(configModel);
    }

    private Map<String, String> configMap(Map<String, String> overrides) {
        Map<String, String> config = new HashMap<>();
        config.put(SmsConstants.CONFIG_API_URL, "https://sms.test");
        config.put(SmsConstants.CONFIG_API_TOKEN, "token");
        config.put(SmsConstants.CONFIG_CODE_LENGTH, "6");
        config.put(SmsConstants.CONFIG_CODE_TTL, "300");
        config.put(SmsConstants.CONFIG_SENDER_ID, "Test");
        config.put(SmsConstants.CONFIG_DEFAULT_REGION, "DE");
        config.put(SmsConstants.CONFIG_MAX_ATTEMPTS, "5");
        config.put(SmsConstants.CONFIG_RESEND_COOLDOWN, "0");
        config.putAll(overrides);
        return config;
    }

    private AuthenticationSessionModel mapBackedAuthSession() {
        AuthenticationSessionModel session = mock(AuthenticationSessionModel.class);
        Map<String, String> notes = new HashMap<>();
        when(session.getAuthNote(anyString())).thenAnswer(invocation -> notes.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            notes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(session).setAuthNote(anyString(), anyString());
        org.mockito.Mockito.doAnswer(invocation -> {
            notes.remove(invocation.getArgument(0));
            return null;
        }).when(session).removeAuthNote(anyString());
        return session;
    }
}
