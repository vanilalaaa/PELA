package com.pela.identity.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Giữ phía server phiên khôi phục mật khẩu của Supabase, tra bằng reset token ngẫu nhiên đã cấp cho
 * client. Client không bao giờ nhận access token của phiên khôi phục. Mỗi user tối đa một phiên.
 */
public interface PasswordResetSessionPort {

    /** Lưu phiên cho token; trả phiên cũ của cùng user (nếu có) đã bị thay thế để thu hồi. */
    Optional<PendingPasswordReset> save(String resetToken, PendingPasswordReset reset);

    /** Lấy và xoá ngay (dùng một lần). */
    Optional<PendingPasswordReset> take(String resetToken);

    /** Xoá và trả các phiên đã hết hạn. */
    List<PendingPasswordReset> removeExpired(Instant now);

    record PendingPasswordReset(UUID userId, String recoveryAccessToken, Instant expiresAt) {

        public boolean isExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }

        @Override
        public String toString() {
            return "PendingPasswordReset[userId=" + userId + ", expiresAt=" + expiresAt + "]";
        }
    }
}
