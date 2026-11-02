package com.pela.identity.application.port.in;

import java.util.UUID;

/**
 * Chặn mọi request đã xác thực của tài khoản suspended (còn hạn) hoặc pending_deletion, kể cả khi JWT
 * còn hạn. Đọc từ bảng users, cache ngắn hạn.
 */
public interface AccountStatusGuardUseCase {

    /** Ném ForbiddenException ACCOUNT_SUSPENDED / ACCOUNT_PENDING_DELETION nếu tài khoản không được dùng. */
    void requireUsableAccount(UUID userId);
}
