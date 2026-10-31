package com.pela.identity.application.usecase;

import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.ForbiddenException;
import com.pela.shared.domain.exception.LockedException;
import com.pela.shared.domain.exception.PelaException;
import com.pela.shared.domain.exception.ServiceUnavailableException;
import com.pela.shared.domain.exception.TooManyRequestsException;
import com.pela.shared.domain.exception.UnauthorizedException;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mã lỗi của module identity và cách đổi lỗi Supabase sang lỗi nghiệp vụ. */
final class AuthErrors {

    private AuthErrors() {
    }

    static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect", null);
    }

    static LockedException accountLocked(Instant lockedUntil) {
        return new LockedException("ACCOUNT_LOCKED", "Too many failed login attempts",
                Map.of("lockedUntil", lockedUntil.toString()));
    }

    static ForbiddenException accountSuspended(UserAccount account) {
        Map<String, Object> details = new HashMap<>();
        details.put("reason", account.suspendReason());
        details.put("suspendedUntil", account.suspendedUntil() == null ? null : account.suspendedUntil().toString());
        return new ForbiddenException("ACCOUNT_SUSPENDED", "Account is suspended", details);
    }

    static ForbiddenException emailNotVerified() {
        return new ForbiddenException("EMAIL_NOT_VERIFIED", "Email address has not been verified", null);
    }

    static ConflictException emailAlreadyRegistered() {
        return new ConflictException("EMAIL_ALREADY_REGISTERED", "Email is already registered", null);
    }

    static ValidationFailedException invalidPassword(String field, List<String> violations) {
        List<Map<String, String>> details = violations.stream()
                .map(v -> Map.of("field", field, "message", v))
                .toList();
        return new ValidationFailedException("VALIDATION_FAILED", "Password does not meet the requirements", details);
    }

    static ValidationFailedException passwordMismatch() {
        return new ValidationFailedException("VALIDATION_FAILED", "Passwords do not match",
                List.of(Map.of("field", "confirmPassword", "message", "must match password")));
    }

    /** Lỗi Supabase không thuộc luồng nghiệp vụ riêng của use case. */
    static PelaException fromSupabase(SupabaseAuthException ex) {
        return switch (ex.reason()) {
            case INVALID_CREDENTIALS -> invalidCredentials();
            case EMAIL_NOT_CONFIRMED -> emailNotVerified();
            case USER_ALREADY_EXISTS -> emailAlreadyRegistered();
            case WEAK_PASSWORD -> new ValidationFailedException("WEAK_PASSWORD", "Password is too weak", null);
            case INVALID_INPUT -> new ValidationFailedException("VALIDATION_FAILED", "Request was rejected as invalid", null);
            case SAME_PASSWORD -> new ValidationFailedException("SAME_PASSWORD",
                    "New password must be different from the old one", null);
            case INVALID_OTP -> new ValidationFailedException("INVALID_OTP", "Code is invalid or has expired", null);
            case INVALID_REFRESH_TOKEN -> new UnauthorizedException("INVALID_REFRESH_TOKEN",
                    "Refresh token is invalid or expired", null);
            case INVALID_ACCESS_TOKEN -> new UnauthorizedException("INVALID_TOKEN",
                    "Access token is invalid or expired", null);
            case RATE_LIMITED -> new TooManyRequestsException("AUTH_RATE_LIMITED",
                    "Too many requests, please try again later", null);
            case USER_NOT_FOUND, UNAVAILABLE, UNKNOWN -> new ServiceUnavailableException("AUTH_PROVIDER_UNAVAILABLE",
                    "Authentication service is unavailable", null);
        };
    }
}
