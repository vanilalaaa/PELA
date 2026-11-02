package com.pela.identity.domain;

import java.time.Duration;
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
        String suspendReason,
        Instant deletionRequestedAt) {

    /** Đang bị khoá bởi Admin; suspendedUntil null = khoá vĩnh viễn (UC-16). */
    public boolean isSuspendedAt(Instant now) {
        return status == AccountStatus.SUSPENDED && (suspendedUntil == null || now.isBefore(suspendedUntil));
    }

    /** Khoá có thời hạn đã hết nhưng chưa được mở (job chạy theo giờ). */
    public boolean suspensionExpiredAt(Instant now) {
        return status == AccountStatus.SUSPENDED && suspendedUntil != null && !now.isBefore(suspendedUntil);
    }

    public boolean isPendingDeletion() {
        return status == AccountStatus.PENDING_DELETION;
    }

    /** BR-33: xoá hẳn sau {@code graceDays} ngày kể từ khi yêu cầu; null nếu thiếu mốc yêu cầu. */
    public Instant deletionScheduledAt(int graceDays) {
        return deletionRequestedAt == null ? null : deletionRequestedAt.plus(Duration.ofDays(graceDays));
    }

    /** SEC-3: chỉ role = admin và status = active mới được dùng chức năng Admin. */
    public boolean isActiveAdmin() {
        return role == Role.ADMIN && status == AccountStatus.ACTIVE;
    }
}
