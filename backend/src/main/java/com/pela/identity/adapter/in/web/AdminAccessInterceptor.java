package com.pela.identity.adapter.in.web;

import com.pela.identity.application.port.in.AdminAccessUseCase;
import com.pela.shared.adapter.in.web.AuthenticatedJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * SEC-3: chặn mọi /v1/admin/** nếu người gọi không phải Admin đang hoạt động (đọc từ bảng users).
 * Lỗi đi qua GlobalExceptionHandler (403 ADMIN_REQUIRED).
 */
public class AdminAccessInterceptor implements HandlerInterceptor {

    private final ObjectProvider<AdminAccessUseCase> adminAccess;

    public AdminAccessInterceptor(ObjectProvider<AdminAccessUseCase> adminAccess) {
        this.adminAccess = adminAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Thiếu bean (vd test slice không nạp use case) thì từ chối thay vì cho qua
        AdminAccessUseCase useCase = adminAccess.getIfAvailable();
        if (useCase == null) {
            throw new IllegalStateException("AdminAccessUseCase is not available");
        }
        useCase.requireActiveAdmin(AuthenticatedJwt.currentUserId());
        return true;
    }
}
