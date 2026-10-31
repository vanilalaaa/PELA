package com.pela.shared.adapter.in.web;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/** Lỗi 401/403 dùng chung cho filter của Spring Security và {@link GlobalExceptionHandler}. */
public final class SecurityErrors {

    private SecurityErrors() {
    }

    public static ApiErrorResponse unauthenticated(AuthenticationException ex) {
        if (ex instanceof OAuth2AuthenticationException) {
            return ApiErrorResponse.of("INVALID_TOKEN", "Access token is invalid or expired");
        }
        return ApiErrorResponse.of("UNAUTHENTICATED", "Authentication is required");
    }

    public static ApiErrorResponse forbidden() {
        return ApiErrorResponse.of("FORBIDDEN", "You do not have permission to perform this action");
    }
}
