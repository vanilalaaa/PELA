package com.pela.identity.application.port.out;

import java.util.UUID;

/**
 * Supabase Auth (GoTrue, {@code {SUPABASE_URL}/auth/v1}). Mọi lỗi được ném dưới dạng
 * {@link SupabaseAuthException} với {@link SupabaseAuthException.Reason}; không bao giờ chứa email,
 * mật khẩu hay token trong message.
 */
public interface SupabaseAuthPort {

    /** Tạo tài khoản; Supabase gửi email xác minh (Auth.Register.Verify). Trả id của auth.users. */
    UUID signUp(String email, String password, String fullName);

    SupabaseSession signInWithPassword(String email, String password);

    SupabaseSession refresh(String refreshToken);

    void logout(String accessToken, LogoutScope scope);

    /** Gửi mã khôi phục 6 số (Auth.Recover.Request). */
    void resetPasswordForEmail(String email);

    /** Xác minh mã khôi phục (type = recovery); trả phiên tạm để đặt mật khẩu mới. */
    SupabaseSession verifyRecoveryOtp(String email, String code);

    void updatePassword(String accessToken, String newPassword);

    /** Xoá hẳn auth.users (Admin API, dùng service role key). */
    void deleteUser(UUID userId);

    enum LogoutScope {
        /** Chỉ phiên hiện tại. */
        LOCAL,
        /** Mọi thiết bị (Auth.Logout.MultiDevice). */
        GLOBAL,
        /** Mọi phiên trừ phiên hiện tại. */
        OTHERS
    }
}
