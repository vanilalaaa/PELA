package com.pela.identity.application.port.in;

import java.util.UUID;

/**
 * SEC-3: chỉ role = admin và status = active mới được gọi Admin.*. Đọc từ bảng users (không tin claim
 * trong JWT), cache ngắn hạn.
 */
public interface AdminAccessUseCase {

    /** Ném ForbiddenException nếu không phải Admin đang hoạt động. */
    void requireActiveAdmin(UUID userId);
}
