package com.pela.identity.adapter.out.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthException.Reason;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseSession;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Gọi Supabase Auth (GoTrue) qua REST. Chỉ log tên thao tác và mã HTTP — không log email, mật khẩu,
 * token hay body (AGENTS mục 7).
 */
public class SupabaseAuthRestAdapter implements SupabaseAuthPort {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthRestAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final SupabaseProperties properties;

    public SupabaseAuthRestAdapter(RestClient client, SupabaseProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public UUID signUp(String email, String password, String fullName) {
        JsonNode body = call("signUp", Reason.UNKNOWN, () -> publicPost("/signup")
                .body(Map.of("email", email, "password", password, "data", Map.of("full_name", fullName)))
                .retrieve().onStatus(HttpStatusCode::isError, errors("signUp", Reason.UNKNOWN))
                .body(JsonNode.class));
        // Bật xác nhận email: trả user; tắt xác nhận: trả session có user
        JsonNode user = body.hasNonNull("user") ? body.get("user") : body;
        return UUID.fromString(user.path("id").asText());
    }

    @Override
    public SupabaseSession signInWithPassword(String email, String password) {
        return session("signIn", Reason.INVALID_CREDENTIALS, () -> publicPost("/token?grant_type=password")
                .body(Map.of("email", email, "password", password)));
    }

    @Override
    public SupabaseSession refresh(String refreshToken) {
        return session("refresh", Reason.INVALID_REFRESH_TOKEN, () -> publicPost("/token?grant_type=refresh_token")
                .body(Map.of("refresh_token", refreshToken)));
    }

    @Override
    public void logout(String accessToken, LogoutScope scope) {
        call("logout", Reason.INVALID_ACCESS_TOKEN, () -> publicPost("/logout?scope=" + scope.name().toLowerCase(Locale.ROOT))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve().onStatus(HttpStatusCode::isError, errors("logout", Reason.INVALID_ACCESS_TOKEN))
                .toBodilessEntity());
    }

    @Override
    public void resetPasswordForEmail(String email) {
        call("recover", Reason.UNKNOWN, () -> publicPost("/recover")
                .body(Map.of("email", email))
                .retrieve().onStatus(HttpStatusCode::isError, errors("recover", Reason.UNKNOWN))
                .toBodilessEntity());
    }

    @Override
    public SupabaseSession verifyRecoveryOtp(String email, String code) {
        return session("verifyOtp", Reason.INVALID_OTP, () -> publicPost("/verify")
                .body(Map.of("type", "recovery", "email", email, "token", code)));
    }

    @Override
    public void updatePassword(String accessToken, String newPassword) {
        call("updatePassword", Reason.INVALID_ACCESS_TOKEN, () -> client.put().uri("/user")
                .header("apikey", properties.anonKey())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("password", newPassword))
                .retrieve().onStatus(HttpStatusCode::isError, errors("updatePassword", Reason.INVALID_ACCESS_TOKEN))
                .toBodilessEntity());
    }

    @Override
    public void deleteUser(UUID userId) {
        if (properties.serviceRoleKey().isEmpty()) {
            throw new SupabaseAuthException(Reason.UNAVAILABLE, "SUPABASE_SERVICE_ROLE_KEY is not configured");
        }
        call("deleteUser", Reason.UNKNOWN, () -> client.delete().uri("/admin/users/{id}", userId)
                .header("apikey", properties.serviceRoleKey())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                .retrieve().onStatus(HttpStatusCode::isError, errors("deleteUser", Reason.UNKNOWN))
                .toBodilessEntity());
    }

    private static RestClient.ResponseSpec.ErrorHandler errors(String operation, Reason clientErrorReason) {
        return (request, response) -> {
            throw failure(operation, response, clientErrorReason);
        };
    }

    private RestClient.RequestBodySpec publicPost(String path) {
        return client.post().uri(path)
                .header("apikey", properties.anonKey())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private SupabaseSession session(String operation, Reason clientErrorReason, Supplier<RestClient.RequestBodySpec> request) {
        JsonNode body = call(operation, clientErrorReason, () -> request.get()
                .retrieve().onStatus(HttpStatusCode::isError, errors(operation, clientErrorReason))
                .body(JsonNode.class));
        JsonNode user = body.path("user");
        return new SupabaseSession(
                body.path("access_token").asText(),
                body.path("refresh_token").asText(null),
                body.path("expires_in").asLong(),
                body.path("token_type").asText("bearer"),
                UUID.fromString(user.path("id").asText()),
                user.hasNonNull("email_confirmed_at") || user.hasNonNull("confirmed_at"));
    }

    private <T> T call(String operation, Reason clientErrorReason, Supplier<T> request) {
        if (properties.url().isEmpty()) {
            throw new SupabaseAuthException(Reason.UNAVAILABLE, "SUPABASE_URL is not configured");
        }
        try {
            return request.get();
        } catch (SupabaseAuthException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("Supabase Auth {} failed: {}", operation, ex.getClass().getSimpleName());
            throw new SupabaseAuthException(Reason.UNAVAILABLE, "Supabase Auth is unreachable", ex);
        }
    }

    private static SupabaseAuthException failure(String operation, ClientHttpResponse response, Reason clientErrorReason)
            throws IOException {
        int status = response.getStatusCode().value();
        JsonNode error = readQuietly(response.getBody());
        Reason reason = classify(status, error, clientErrorReason);
        log.info("Supabase Auth {} rejected: status={}, reason={}", operation, status, reason);
        return new SupabaseAuthException(reason, "Supabase Auth " + operation + " failed with " + reason);
    }

    /** Phân loại theo error_code của GoTrue (bản mới), rồi theo error/msg (bản cũ), cuối cùng theo mã HTTP. */
    static Reason classify(int status, JsonNode error, Reason clientErrorReason) {
        String code = error.path("error_code").asText("");
        switch (code) {
            case "invalid_credentials":
                return Reason.INVALID_CREDENTIALS;
            case "email_not_confirmed":
                return Reason.EMAIL_NOT_CONFIRMED;
            case "user_already_exists", "email_exists":
                return Reason.USER_ALREADY_EXISTS;
            case "weak_password":
                return Reason.WEAK_PASSWORD;
            case "validation_failed", "email_address_invalid", "bad_json":
                return Reason.INVALID_INPUT;
            case "same_password":
                return Reason.SAME_PASSWORD;
            case "otp_expired":
                return Reason.INVALID_OTP;
            case "refresh_token_not_found", "refresh_token_already_used":
                return Reason.INVALID_REFRESH_TOKEN;
            case "bad_jwt", "no_authorization", "session_not_found", "session_expired":
                return clientErrorReason == Reason.INVALID_REFRESH_TOKEN ? Reason.INVALID_REFRESH_TOKEN : Reason.INVALID_ACCESS_TOKEN;
            case "user_not_found":
                return Reason.USER_NOT_FOUND;
            case "over_request_rate_limit", "over_email_send_rate_limit", "over_sms_send_rate_limit":
                return Reason.RATE_LIMITED;
            default:
                break;
        }
        String legacy = (error.path("error_description").asText("") + " " + error.path("msg").asText("")).toLowerCase(Locale.ROOT);
        if (legacy.contains("invalid login credentials")) {
            return Reason.INVALID_CREDENTIALS;
        }
        if (legacy.contains("email not confirmed")) {
            return Reason.EMAIL_NOT_CONFIRMED;
        }
        if (status == 429) {
            return Reason.RATE_LIMITED;
        }
        if (status >= 500) {
            return Reason.UNAVAILABLE;
        }
        return clientErrorReason;
    }

    private static JsonNode readQuietly(InputStream body) {
        try {
            JsonNode node = JSON.readTree(body);
            return node == null ? JSON.createObjectNode() : node;
        } catch (IOException ex) {
            return JSON.createObjectNode();
        }
    }
}
