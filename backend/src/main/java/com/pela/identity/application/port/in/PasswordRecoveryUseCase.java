package com.pela.identity.application.port.in;

/** UC-01 2.0.A2 / Auth.Recover: quên mật khẩu bằng mã 6 số. */
public interface PasswordRecoveryUseCase {

    /** Luôn trả bình thường dù email có tồn tại hay không (không để lộ tài khoản). */
    void requestReset(String email);

    /**
     * Mã đúng → trả reset token ngẫu nhiên dùng một lần (TTL theo setting auth.password_reset_ttl_minutes).
     * Phiên khôi phục của Supabase được giữ phía server, không trả cho client.
     */
    ResetToken verifyCode(String email, String code);

    void resetPassword(String resetToken, String newPassword, String passwordConfirmation);

    /** Dọn reset token quá hạn và thu hồi phiên khôi phục tương ứng (job định kỳ). */
    void purgeExpired();

    /** Token ngẫu nhiên dùng một lần cho bước đặt mật khẩu mới. */
    record ResetToken(String token, long expiresInSeconds) {

        @Override
        public String toString() {
            return "ResetToken[expiresIn=" + expiresInSeconds + "]";
        }
    }
}
