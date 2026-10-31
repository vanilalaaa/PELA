package com.pela.shared.adapter.in.web;

import java.util.UUID;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Đọc userId (claim {@code sub}) của request hiện tại từ JWT đã xác thực — nguồn userId duy nhất (SEC-4). */
public final class AuthenticatedJwt {

    private AuthenticatedJwt() {
    }

    public static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new InsufficientAuthenticationException("JWT authentication is required");
        }
        String subject = jwt.getToken().getSubject();
        if (subject == null) {
            throw new InvalidBearerTokenException("Token has no subject");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Token subject is not a UUID");
        }
    }
}
