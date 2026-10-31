package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.PasswordRecoveryUseCase;
import com.pela.identity.application.port.out.PasswordResetSessionPort;
import com.pela.identity.application.port.out.PasswordResetSessionPort.PendingPasswordReset;
import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthException.Reason;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import com.pela.identity.domain.PasswordPolicy;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.SecureTokenPort;
import com.pela.shared.application.port.out.SystemSettingPort;
import com.pela.shared.domain.exception.UnauthorizedException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Auth.Recover: Supabase gửi mã 6 số (hạn 5 phút cấu hình ở Supabase Auth). Mã đúng → backend giữ phiên
 * khôi phục phía server và trả cho client reset token ngẫu nhiên dùng một lần; đặt mật khẩu mới theo
 * Auth.Register.Validate.
 */
@Service
public class PasswordRecoveryService implements PasswordRecoveryUseCase {

    static final String RESET_TTL_SETTING = "auth.password_reset_ttl_minutes";

    private final SupabaseAuthPort auth;
    private final PasswordResetSessionPort sessions;
    private final SecureTokenPort tokens;
    private final SystemSettingPort settings;
    private final ClockPort clock;

    public PasswordRecoveryService(SupabaseAuthPort auth, PasswordResetSessionPort sessions, SecureTokenPort tokens,
            SystemSettingPort settings, ClockPort clock) {
        this.auth = auth;
        this.sessions = sessions;
        this.tokens = tokens;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public void requestReset(String email) {
        try {
            auth.resetPasswordForEmail(AuthService.normalize(email));
        } catch (SupabaseAuthException ex) {
            // Chỉ báo lỗi hạ tầng / quá tần suất; không để lộ email có tồn tại hay không
            if (ex.reason() == Reason.RATE_LIMITED || ex.reason() == Reason.UNAVAILABLE) {
                throw AuthErrors.fromSupabase(ex);
            }
        }
    }

    @Override
    public ResetToken verifyCode(String email, String code) {
        SupabaseSession session;
        try {
            session = auth.verifyRecoveryOtp(AuthService.normalize(email), code);
        } catch (SupabaseAuthException ex) {
            throw AuthErrors.fromSupabase(ex);
        }
        Duration ttl = Duration.ofMinutes(settings.requiredInt(RESET_TTL_SETTING));
        String resetToken = tokens.newToken();
        sessions.save(resetToken, new PendingPasswordReset(session.userId(), session.accessToken(), clock.now().plus(ttl)))
                .ifPresent(this::revokeQuietly);
        return new ResetToken(resetToken, ttl.toSeconds());
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
        // Lấy ra là xoá luôn: token chỉ dùng được một lần
        PendingPasswordReset pending = sessions.take(resetToken).orElseThrow(PasswordRecoveryService::invalidResetToken);
        if (pending.isExpiredAt(clock.now())) {
            revokeQuietly(pending);
            throw invalidResetToken();
        }
        try {
            auth.updatePassword(pending.recoveryAccessToken(), newPassword);
        } catch (SupabaseAuthException ex) {
            if (ex.reason() == Reason.INVALID_ACCESS_TOKEN) {
                throw invalidResetToken();
            }
            // Mật khẩu bị Supabase từ chối (yếu / trùng) hay lỗi tạm thời: trả token lại để người dùng thử tiếp
            sessions.save(resetToken, pending);
            throw AuthErrors.fromSupabase(ex);
        }
        revokeQuietly(pending);
    }

    @Override
    public void purgeExpired() {
        sessions.removeExpired(clock.now()).forEach(this::revokeQuietly);
    }

    private static UnauthorizedException invalidResetToken() {
        return new UnauthorizedException("INVALID_RESET_TOKEN", "Reset token is invalid or expired", null);
    }

    private void revokeQuietly(PendingPasswordReset pending) {
        try {
            auth.logout(pending.recoveryAccessToken(), LogoutScope.LOCAL);
        } catch (SupabaseAuthException ignored) {
            // Phiên khôi phục tự hết hạn theo JWT
        }
    }
}
