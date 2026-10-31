package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.AuthTokens;
import com.pela.identity.application.port.in.LoginUseCase;
import com.pela.identity.application.port.in.LogoutUseCase;
import com.pela.identity.application.port.in.RefreshSessionUseCase;
import com.pela.identity.application.port.in.RegisterUseCase;
import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.identity.domain.AccountStatus;
import com.pela.identity.domain.LockoutPolicy;
import com.pela.identity.domain.LoginState;
import com.pela.identity.domain.PasswordPolicy;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.SettingsPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** UC-01: đăng ký, đăng nhập (kèm lockout SEC-5), làm mới phiên, đăng xuất. */
@Service
public class AuthService implements RegisterUseCase, LoginUseCase, RefreshSessionUseCase, LogoutUseCase {

    static final String MAX_ATTEMPTS_SETTING = "auth.lockout_max_attempts";
    static final String LOCKOUT_WINDOW_SETTING = "auth.lockout_window_minutes";
    static final String LOCKOUT_MINUTES_SETTING = "auth.lockout_minutes";

    private final SupabaseAuthPort auth;
    private final UserAccountPort accounts;
    private final SettingsPort settings;
    private final ClockPort clock;
    private final AccountStanding standing;

    AuthService(SupabaseAuthPort auth, UserAccountPort accounts, SettingsPort settings, ClockPort clock,
            AccountStanding standing) {
        this.auth = auth;
        this.accounts = accounts;
        this.settings = settings;
        this.clock = clock;
        this.standing = standing;
    }

    @Override
    public RegisteredAccount register(RegisterCommand command) {
        List<String> violations = PasswordPolicy.violations(command.password());
        if (!violations.isEmpty()) {
            throw AuthErrors.invalidPassword("password", violations);
        }
        if (!Objects.equals(command.password(), command.passwordConfirmation())) {
            throw AuthErrors.passwordMismatch();
        }
        String email = normalize(command.email());
        // BR-1 / Auth.Register.Duplicate: báo rõ để app đưa sang màn Login
        if (accounts.findByEmail(email).isPresent()) {
            throw AuthErrors.emailAlreadyRegistered();
        }
        try {
            var userId = auth.signUp(email, command.password(), command.fullName().trim());
            return new RegisteredAccount(userId, email, AccountStatus.UNVERIFIED.dbValue());
        } catch (SupabaseAuthException ex) {
            throw AuthErrors.fromSupabase(ex);
        }
    }

    @Override
    public AuthTokens login(String rawEmail, String password) {
        String email = normalize(rawEmail);
        Instant now = clock.now();
        UserAccount account = accounts.findByEmail(email).orElseThrow(AuthErrors::invalidCredentials);

        // Kiểm tra khoá trước khi hỏi Supabase: đang khoá thì mật khẩu đúng cũng không vào được
        if (account.loginState().isLockedAt(now)) {
            throw AuthErrors.accountLocked(account.loginState().lockedUntil());
        }

        SupabaseSession session;
        try {
            session = auth.signInWithPassword(email, password);
        } catch (SupabaseAuthException ex) {
            if (ex.reason() == SupabaseAuthException.Reason.INVALID_CREDENTIALS) {
                LockoutPolicy policy = lockoutPolicy();
                LoginState state = accounts.updateLoginState(account.userId(), s -> policy.onFailure(s, now));
                if (state.isLockedAt(now)) {
                    throw AuthErrors.accountLocked(state.lockedUntil());
                }
                throw AuthErrors.invalidCredentials();
            }
            throw AuthErrors.fromSupabase(ex);
        }

        accounts.updateLoginState(account.userId(), s -> LoginState.CLEAN);

        // Chỉ báo lý do khoá / chờ xoá / chưa xác minh khi mật khẩu đã đúng; phiên vừa cấp bị thu hồi ngay
        requireUsable(account, now, session);
        if (account.status() == AccountStatus.UNVERIFIED) {
            if (!session.emailConfirmed()) {
                revokeQuietly(session);
                throw AuthErrors.emailNotVerified();
            }
            accounts.markEmailVerified(account.userId());
        }
        return tokens(session);
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        SupabaseSession session;
        try {
            session = auth.refresh(refreshToken);
        } catch (SupabaseAuthException ex) {
            throw AuthErrors.fromSupabase(ex);
        }
        Instant now = clock.now();
        UserAccount account = accounts.findById(session.userId()).orElse(null);
        if (account != null) {
            requireUsable(account, now, session);
        }
        return tokens(session);
    }

    @Override
    public void logout(String accessToken, boolean allDevices) {
        try {
            auth.logout(accessToken, allDevices ? LogoutScope.GLOBAL : LogoutScope.LOCAL);
        } catch (SupabaseAuthException ex) {
            // Phiên đã hết hạn/không còn: coi như đã đăng xuất
            if (ex.reason() != SupabaseAuthException.Reason.INVALID_ACCESS_TOKEN
                    && ex.reason() != SupabaseAuthException.Reason.USER_NOT_FOUND) {
                throw AuthErrors.fromSupabase(ex);
            }
        }
    }

    private LockoutPolicy lockoutPolicy() {
        return new LockoutPolicy(settings.requiredInt(MAX_ATTEMPTS_SETTING),
                Duration.ofMinutes(settings.requiredInt(LOCKOUT_WINDOW_SETTING)),
                Duration.ofMinutes(settings.requiredInt(LOCKOUT_MINUTES_SETTING)));
    }

    private void requireUsable(UserAccount account, Instant now, SupabaseSession session) {
        try {
            standing.requireUsable(account, now);
        } catch (RuntimeException ex) {
            revokeQuietly(session);
            throw ex;
        }
    }

    private void revokeQuietly(SupabaseSession session) {
        try {
            auth.logout(session.accessToken(), LogoutScope.LOCAL);
        } catch (SupabaseAuthException ignored) {
            // Không chặn việc trả lỗi cho client; token sẽ tự hết hạn
        }
    }

    private static AuthTokens tokens(SupabaseSession session) {
        return new AuthTokens(session.accessToken(), session.refreshToken(), session.expiresInSeconds(),
                session.tokenType());
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
