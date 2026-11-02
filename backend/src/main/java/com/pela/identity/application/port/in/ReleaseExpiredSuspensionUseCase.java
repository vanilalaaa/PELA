package com.pela.identity.application.port.in;

import java.util.UUID;

/**
 * UC-16 A2 Automatic Unsuspend: mở khoá tài khoản khi suspended_until đã qua. Login, bộ lọc trạng thái
 * tài khoản và job theo giờ đều đi qua use case này. Idempotent: gọi lại không đổi gì thêm.
 */
public interface ReleaseExpiredSuspensionUseCase {

    /** Mở khoá một tài khoản nếu hết hạn; trả true nếu vừa mở. */
    boolean releaseIfExpired(UUID userId);

    /** Mở khoá mọi tài khoản đã hết hạn; trả số tài khoản vừa mở. */
    int releaseAllExpired();
}
