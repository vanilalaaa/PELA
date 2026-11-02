package com.pela.identity.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.identity.application.port.in.AccountStatusGuardUseCase;
import com.pela.shared.adapter.in.web.ApiErrorResponse;
import com.pela.shared.domain.exception.PelaException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chặn request đã xác thực tới /v1/** của tài khoản suspended (còn hạn) hoặc pending_deletion, dù JWT
 * vẫn còn hạn. Đặt sau bước xác thực JWT trong security filter chain. /v1/auth/logout luôn cho qua để
 * tài khoản bị chặn vẫn thoát được phiên.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private static final String LOGOUT_PATH = "/v1/auth/logout";

    private final ObjectProvider<AccountStatusGuardUseCase> guard;
    private final ObjectMapper objectMapper;

    public AccountStatusFilter(ObjectProvider<AccountStatusGuardUseCase> guard, ObjectMapper objectMapper) {
        this.guard = guard;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/v1/") || path.equals(LOGOUT_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = subject(authentication);
        if (userId != null) {
            // Thiếu bean thì từ chối thay vì cho qua (fail closed)
            AccountStatusGuardUseCase useCase = guard.getIfAvailable();
            if (useCase == null) {
                throw new IllegalStateException("AccountStatusGuardUseCase is not available");
            }
            try {
                useCase.requireUsableAccount(userId);
            } catch (PelaException ex) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(),
                        ApiErrorResponse.of(ex.code(), ex.getMessage(), ex.details()));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** userId từ JWT; null nếu request không xác thực bằng JWT hoặc sub không phải UUID (để bước sau trả 401). */
    private static UUID subject(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || jwt.getToken().getSubject() == null) {
            return null;
        }
        try {
            return UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
