package com.pela.identity.domain;

import java.time.Instant;
import java.util.UUID;

/** Tài khoản PELA (bảng users) — bản sao hồ sơ cạnh auth.users của Supabase. */
public record UserAccount(
        UUID userId,
        String email,
        String fullName,
        String username,
        String avatarUrl,
        Role role,
        AccountStatus status,
        String cefrLevel,
        String timezone,
        LoginState loginState,
        Instant suspendedUntil,
        String suspendReason) {

    /** Đang bị khoá bởi Admin; suspendedUntil null = khoá vĩnh viễn (UC-16). */
    public boolean isSuspendedAt(Instant now) {
        return status == AccountStatus.SUSPENDED && (suspendedUntil == null || now.isBefore(suspendedUntil));
    }

    /** Khoá có thời hạn đã hết nhưng job tự mở khoá chưa chạy. */
    public boolean suspensionExpiredAt(Instant now) {
        return status == AccountStatus.SUSPENDED && suspendedUntil != null && !now.isBefore(suspendedUntil);
    }

    /** SEC-3: chỉ role = admin và status = active mới được dùng chức năng Admin. */
    public boolean isActiveAdmin() {
        return role == Role.ADMIN && status == AccountStatus.ACTIVE;
    }
}
