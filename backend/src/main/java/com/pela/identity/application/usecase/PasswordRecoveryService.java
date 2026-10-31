package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.PasswordRecoveryUseCase;
import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import com.pela.identity.domain.PasswordPolicy;
import com.pela.shared.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Auth.Recover: Supabase gửi mã 6 số (hạn 5 phút cấu hình ở Supabase Auth), xác minh mã đổi lấy phiên
 * khôi phục, rồi đặt mật khẩu mới theo Auth.Register.Validate.
 */
@Service
public class PasswordRecoveryService implements PasswordRecoveryUseCase {

    private final SupabaseAuthPort auth;

    public PasswordRecoveryService(SupabaseAuthPort auth) {
        this.auth = auth;
    }

    @Override
    public void requestReset(String email) {
        try {
            auth.resetPasswordForEmail(AuthService.normalize(email));
        } catch (SupabaseAuthException ex) {
            // Chỉ báo lỗi hạ tầng / quá tần suất; không để lộ email có tồn tại hay không
            if (ex.reason() == SupabaseAuthException.Reason.RATE_LIMITED
                    || ex.reason() == SupabaseAuthException.Reason.UNAVAILABLE) {
                throw AuthErrors.fromSupabase(ex);
            }
        }
    }

    @Override
    public ResetToken verifyCode(String email, String code) {
        try {
            SupabaseSession session = auth.verifyRecoveryOtp(AuthService.normalize(email), code);
            return new ResetToken(session.accessToken(), session.expiresInSeconds());
        } catch (SupabaseAuthException ex) {
            throw AuthErrors.fromSupabase(ex);
        }
    }

    @Override
    public void resetPassword(String resetToken, String newPassword, String passwordConfirmation) {
        List<String> violations = PasswordPolicy.violations(newPassword);
        if (!violations.isEmpty()) {
            throw AuthErrors.invalidPassword("newPassword", violations);
        }
        if (!Objects.equals(newPassword, passwordConfirmation)) {
            throw AuthErrors.passwordMismatch();
        }
        try {
            auth.updatePassword(resetToken, newPassword);
        } catch (SupabaseAuthException ex) {
            if (ex.reason() == SupabaseAuthException.Reason.INVALID_ACCESS_TOKEN) {
                throw new UnauthorizedException("INVALID_RESET_TOKEN",
                        "Reset token is invalid or expired", null);
            }
            throw AuthErrors.fromSupabase(ex);
        }
        // Phiên khôi phục chỉ dùng một lần
        try {
            auth.logout(resetToken, LogoutScope.LOCAL);
        } catch (SupabaseAuthException ignored) {
            // Token tự hết hạn
        }
    }
}
