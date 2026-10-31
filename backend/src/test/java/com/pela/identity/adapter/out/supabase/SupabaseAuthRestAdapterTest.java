package com.pela.identity.adapter.out.supabase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthException.Reason;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseAuthRestAdapterTest {

    private static final String BASE = "https://project.supabase.test/auth/v1";
    private static final UUID USER_ID = UUID.fromString("7d3c5a2e-1b4f-4c8a-9e6d-2f1a0b3c4d5e");
    private static final String SESSION_JSON = """
            {"access_token":"at","token_type":"bearer","expires_in":604800,"refresh_token":"rt",
             "user":{"id":"7d3c5a2e-1b4f-4c8a-9e6d-2f1a0b3c4d5e","email_confirmed_at":"2026-11-01T08:00:00Z"}}
            """;

    private MockRestServiceServer server;
    private SupabaseAuthRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SupabaseAuthRestAdapter(builder.build(),
                new SupabaseProperties("https://project.supabase.test/", "anon-key", "service-key"));
    }

    @Test
    void signInPostsPasswordGrantWithAnonApiKey() {
        server.expect(requestTo(BASE + "/token?grant_type=password"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "anon-key"))
                .andExpect(content().json("{\"email\":\"a@example.test\",\"password\":\"Secret123\"}"))
                .andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON));

        SupabaseSession session = adapter.signInWithPassword("a@example.test", "Secret123");

        assertThat(session.accessToken()).isEqualTo("at");
        assertThat(session.refreshToken()).isEqualTo("rt");
        assertThat(session.expiresInSeconds()).isEqualTo(604800);
        assertThat(session.userId()).isEqualTo(USER_ID);
        assertThat(session.emailConfirmed()).isTrue();
        assertThat(session.toString()).doesNotContain("at\"", "rt");
        server.verify();
    }

    @Test
    void signUpSendsFullNameMetadataAndReadsUserId() {
        server.expect(requestTo(BASE + "/signup"))
                .andExpect(content().json("{\"email\":\"a@example.test\",\"data\":{\"full_name\":\"Lan\"}}"))
                .andRespond(withSuccess("{\"id\":\"" + USER_ID + "\",\"email\":\"a@example.test\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.signUp("a@example.test", "Secret123", "Lan")).isEqualTo(USER_ID);
    }

    @Test
    void signUpWithAutoConfirmReturnsSessionShape() {
        server.expect(requestTo(BASE + "/signup")).andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON));

        assertThat(adapter.signUp("a@example.test", "Secret123", "Lan")).isEqualTo(USER_ID);
    }

    @Test
    void refreshUsesRefreshTokenGrant() {
        server.expect(requestTo(BASE + "/token?grant_type=refresh_token"))
                .andExpect(content().json("{\"refresh_token\":\"rt\"}"))
                .andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON));

        assertThat(adapter.refresh("rt").userId()).isEqualTo(USER_ID);
    }

    @Test
    void logoutSendsScopeAndBearerToken() {
        server.expect(requestTo(BASE + "/logout?scope=global"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andExpect(header("apikey", "anon-key"))
                .andRespond(withNoContent());

        adapter.logout("user-token", LogoutScope.GLOBAL);
        server.verify();
    }

    @Test
    void recoverAndVerifyRecoveryOtp() {
        server.expect(requestTo(BASE + "/recover"))
                .andExpect(content().json("{\"email\":\"a@example.test\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/verify"))
                .andExpect(content().json("{\"type\":\"recovery\",\"email\":\"a@example.test\",\"token\":\"123456\"}"))
                .andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON));

        adapter.resetPasswordForEmail("a@example.test");
        assertThat(adapter.verifyRecoveryOtp("a@example.test", "123456").accessToken()).isEqualTo("at");
        server.verify();
    }

    @Test
    void updatePasswordPutsUserWithBearer() {
        server.expect(requestTo(BASE + "/user"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer recovery-token"))
                .andExpect(content().json("{\"password\":\"NewSecret1\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.updatePassword("recovery-token", "NewSecret1");
        server.verify();
    }

    @Test
    void deleteUserUsesServiceRoleKeyOnAdminApi() {
        server.expect(requestTo(BASE + "/admin/users/" + USER_ID))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("apikey", "service-key"))
                .andExpect(header("Authorization", "Bearer service-key"))
                .andRespond(withSuccess());

        adapter.deleteUser(USER_ID);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "400 | {\"code\":400,\"error_code\":\"invalid_credentials\",\"msg\":\"Invalid login credentials\"} | INVALID_CREDENTIALS",
            "400 | {\"error\":\"invalid_grant\",\"error_description\":\"Invalid login credentials\"}       | INVALID_CREDENTIALS",
            "400 | {\"code\":400,\"error_code\":\"email_not_confirmed\",\"msg\":\"Email not confirmed\"}    | EMAIL_NOT_CONFIRMED",
            "429 | {\"code\":429,\"error_code\":\"over_request_rate_limit\"}                               | RATE_LIMITED",
            "500 | {\"msg\":\"boom\"}                                                                      | UNAVAILABLE",
            "400 | not-json                                                                                | INVALID_CREDENTIALS",
    })
    void signInErrorsAreClassified(int status, String body, Reason expected) {
        server.expect(requestTo(BASE + "/token?grant_type=password"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));

        assertThatThrownBy(() -> adapter.signInWithPassword("a@example.test", "wrong"))
                .isInstanceOf(SupabaseAuthException.class)
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(expected))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("a@example.test", "wrong"));
    }

    @Test
    void signUpDuplicateAndWeakPassword() {
        server.expect(requestTo(BASE + "/signup")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":422,\"error_code\":\"user_already_exists\"}"));
        server.expect(requestTo(BASE + "/signup")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":422,\"error_code\":\"weak_password\"}"));

        assertThatThrownBy(() -> adapter.signUp("a@example.test", "x", "A"))
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(Reason.USER_ALREADY_EXISTS));
        assertThatThrownBy(() -> adapter.signUp("a@example.test", "x", "A"))
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(Reason.WEAK_PASSWORD));
    }

    @Test
    void expiredOtpAndRefreshTokenErrors() {
        server.expect(requestTo(BASE + "/verify")).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":403,\"error_code\":\"otp_expired\"}"));
        server.expect(requestTo(BASE + "/token?grant_type=refresh_token")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":400,\"error_code\":\"refresh_token_not_found\"}"));

        assertThatThrownBy(() -> adapter.verifyRecoveryOtp("a@example.test", "000000"))
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(Reason.INVALID_OTP));
        assertThatThrownBy(() -> adapter.refresh("old"))
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(Reason.INVALID_REFRESH_TOKEN));
    }

    @Test
    void networkFailureIsUnavailable() {
        server.expect(requestTo(BASE + "/token?grant_type=password")).andRespond(withException(new IOException("reset")));

        assertThatThrownBy(() -> adapter.signInWithPassword("a@example.test", "x"))
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(Reason.UNAVAILABLE));
    }

    @Test
    void missingConfigurationIsUnavailableWithoutCallingNetwork() {
        var unconfigured = new SupabaseAuthRestAdapter(RestClient.create(), new SupabaseProperties(null, null, null));

        assertThatThrownBy(() -> unconfigured.signInWithPassword("a@example.test", "x"))
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(Reason.UNAVAILABLE));
        assertThatThrownBy(() -> unconfigured.deleteUser(USER_ID))
                .satisfies(e -> assertThat(((SupabaseAuthException) e).reason()).isEqualTo(Reason.UNAVAILABLE));
    }
}
