package com.pela.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthException.Reason;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import com.pela.support.PostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Lockout, chặn tài khoản và admin guard với bảng users thật; chỉ Supabase Auth được mock. */
@AutoConfigureMockMvc
class IdentityIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    SupabaseAuthPort supabase;

    /** Tạo tài khoản như Supabase: insert auth.users, trigger V1 tạo public.users. */
    private UUID createUser(String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"IT User\"}')",
                id, email(id));
        jdbc.update("UPDATE users SET role = ?, status = ? WHERE user_id = ?", role, status, id);
        return id;
    }

    private static String email(UUID id) {
        return "it-" + id + "@example.test";
    }

    private ResultActions login(UUID id, String password) throws Exception {
        return mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email(id) + "\",\"password\":\"" + password + "\"}"));
    }

    private SupabaseSession session(UUID id, boolean emailConfirmed) {
        return new SupabaseSession("access-" + id, "refresh-" + id, 604800, "bearer", id, emailConfirmed);
    }

    @Test
    void fiveWrongPasswordsLockAccountFor15Minutes() throws Exception {
        UUID id = createUser("learner", "active");
        when(supabase.signInWithPassword(email(id), "wrong"))
                .thenThrow(new SupabaseAuthException(Reason.INVALID_CREDENTIALS, "invalid"));
        when(supabase.signInWithPassword(email(id), PASSWORD)).thenReturn(session(id, true));

        for (int i = 1; i <= 4; i++) {
            login(id, "wrong").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM users WHERE user_id = ?", Integer.class, id))
                .isEqualTo(4);

        login(id, "wrong").andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.error.details.lockedUntil").isString());

        Timestamp lockedUntil = jdbc.queryForObject("SELECT locked_until FROM users WHERE user_id = ?", Timestamp.class, id);
        assertThat(Duration.between(Instant.now(), lockedUntil.toInstant()))
                .isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));

        // Đang khoá: mật khẩu đúng cũng bị chặn, không gọi Supabase
        login(id, PASSWORD).andExpect(status().isLocked());
        verify(supabase, never()).signInWithPassword(email(id), PASSWORD);

        // Hết hạn khoá: đăng nhập đúng được và bộ đếm về 0
        jdbc.update("UPDATE users SET locked_until = now() - interval '1 second' WHERE user_id = ?", id);
        login(id, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-" + id))
                .andExpect(jsonPath("$.expiresIn").value(604800));
        assertThat(jdbc.queryForMap(
                "SELECT failed_login_count, failed_login_window_started_at, locked_until FROM users WHERE user_id = ?", id))
                .containsEntry("failed_login_count", 0)
                .containsEntry("failed_login_window_started_at", null)
                .containsEntry("locked_until", null);
    }

    @Test
    void suspendedAccountGets403WithReasonAndEndDate() throws Exception {
        UUID id = createUser("learner", "active");
        jdbc.update("UPDATE users SET status = 'suspended', suspend_reason = 'Spam', suspended_until = ? WHERE user_id = ?",
                Timestamp.from(Instant.parse("2030-01-01T00:00:00Z")), id);
        when(supabase.signInWithPassword(email(id), PASSWORD)).thenReturn(session(id, true));

        login(id, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.error.details.reason").value("Spam"))
                .andExpect(jsonPath("$.error.details.suspendedUntil").value("2030-01-01T00:00:00Z"));
        verify(supabase).logout("access-" + id, LogoutScope.LOCAL);
    }

    @Test
    void unverifiedAccountGets403() throws Exception {
        UUID id = createUser("learner", "unverified");
        when(supabase.signInWithPassword(email(id), PASSWORD)).thenReturn(session(id, false));

        login(id, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void confirmedEmailActivatesAccountOnLogin() throws Exception {
        UUID id = createUser("learner", "unverified");
        when(supabase.signInWithPassword(email(id), PASSWORD)).thenReturn(session(id, true));

        login(id, PASSWORD).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE user_id = ?", String.class, id)).isEqualTo("active");
    }

    @Test
    void adminRoutesRequireActiveAdminFromDatabase() throws Exception {
        UUID learner = createUser("learner", "active");
        UUID admin = createUser("admin", "active");
        UUID suspendedAdmin = createUser("admin", "suspended");

        // Claim role trong JWT bị bỏ qua: chỉ bảng users quyết định (SEC-3)
        mvc.perform(get("/v1/admin/me").with(jwt().jwt(j -> j.subject(learner.toString())
                        .claim("role", "admin").claim("app_metadata", Map.of("role", "admin")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_REQUIRED"));
        mvc.perform(get("/v1/admin/me").with(jwt().jwt(j -> j.subject(suspendedAdmin.toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/me").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/v1/admin/me").with(jwt().jwt(j -> j.subject(admin.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void meReturnsOwnProfileFromJwtSubject() throws Exception {
        UUID id = createUser("learner", "active");

        mvc.perform(get("/v1/me").param("userId", UUID.randomUUID().toString())
                        .with(jwt().jwt(j -> j.subject(id.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.email").value(email(id)))
                .andExpect(jsonPath("$.fullName").value("IT User"))
                .andExpect(jsonPath("$.role").value("learner"));
    }

    @Test
    void registerValidatesBeforeCallingSupabase() throws Exception {
        mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Lan","email":"new@example.test","password":"weakpass","confirmPassword":"weakpass"}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"","email":"not-an-email","password":"","confirmPassword":""}
                        """))
                .andExpect(status().isUnprocessableEntity());
        verify(supabase, never()).signUp(anyString(), anyString(), anyString());
    }

    @Test
    void registerCreatesUnverifiedAccount() throws Exception {
        UUID newId = UUID.randomUUID();
        when(supabase.signUp("lan-" + newId + "@example.test", PASSWORD, "Lan")).thenReturn(newId);

        mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Lan","email":"Lan-%s@Example.test","password":"%s","confirmPassword":"%s"}
                        """.formatted(newId, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(newId.toString()))
                .andExpect(jsonPath("$.status").value("unverified"));
    }

    @Test
    void logoutNeedsTokenAndForwardsScope() throws Exception {
        mvc.perform(post("/v1/auth/logout")).andExpect(status().isUnauthorized());

        mvc.perform(post("/v1/auth/logout").with(jwt().jwt(j -> j.tokenValue("user-token")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allDevices\":true}"))
                .andExpect(status().isNoContent());
        verify(supabase).logout("user-token", LogoutScope.GLOBAL);
    }

    @Test
    void forgotPasswordIsAlwaysAccepted() throws Exception {
        mvc.perform(post("/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.test\"}"))
                .andExpect(status().isAccepted());
        verify(supabase).resetPasswordForEmail("someone@example.test");
    }
}
