package com.pela.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pela.identity.adapter.out.memory.InMemoryPasswordResetSessionAdapter;
import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthException.Reason;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.PelaException;
import com.pela.shared.domain.exception.TooManyRequestsException;
import com.pela.shared.domain.exception.UnauthorizedException;
import com.pela.shared.domain.exception.ValidationFailedException;
import com.pela.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PasswordRecoveryServiceTest {

    private static final String EMAIL = "a@example.test";
    private static final UUID USER_ID = UUID.randomUUID();

    private final SupabaseAuthPort auth = mock(SupabaseAuthPort.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T08:00:00Z"));
    private final AtomicInteger tokenCounter = new AtomicInteger();
    private final SettingsPort settings = key -> Optional.ofNullable(
            Map.of("auth.password_reset_ttl_minutes", "10").get(key));
    private final PasswordRecoveryService service = new PasswordRecoveryService(auth,
            new InMemoryPasswordResetSessionAdapter(), () -> "reset-token-" + tokenCounter.incrementAndGet(),
            settings, clock);

    private void recoveryOtpAccepted(String recoveryAccessToken) {
        when(auth.verifyRecoveryOtp(EMAIL, "123456"))
                .thenReturn(new SupabaseSession(recoveryAccessToken, "r", 3600, "bearer", USER_ID, true));
    }

    private static String code(Throwable t) {
        return ((PelaException) t).code();
    }

    @Test
    void requestResetNormalizesEmail() {
        service.requestReset(" A@Example.test ");

        verify(auth).resetPasswordForEmail(EMAIL);
    }

    @Test
    void requestResetHidesUnknownEmailButReportsRateLimit() {
        doThrow(new SupabaseAuthException(Reason.USER_NOT_FOUND, "x")).when(auth).resetPasswordForEmail("x@example.test");
        service.requestReset("x@example.test");

        doThrow(new SupabaseAuthException(Reason.RATE_LIMITED, "x")).when(auth).resetPasswordForEmail("y@example.test");
        assertThatThrownBy(() -> service.requestReset("y@example.test")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void verifyCodeReturnsRandomTokenNotRecoveryAccessToken() {
        recoveryOtpAccepted("recovery-access-token");

        var token = service.verifyCode(EMAIL, "123456");

        assertThat(token.token()).isEqualTo("reset-token-1").isNotEqualTo("recovery-access-token");
        assertThat(token.expiresInSeconds()).isEqualTo(600);
        assertThat(token.toString()).doesNotContain("reset-token-1", "recovery-access-token");
    }

    @Test
    void wrongOrExpiredCodeIs422InvalidOtp() {
        when(auth.verifyRecoveryOtp(anyString(), anyString())).thenThrow(new SupabaseAuthException(Reason.INVALID_OTP, "x"));

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000"))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_OTP"));
    }

    @Test
    void resetUsesServerSideRecoverySessionThenRevokesIt() {
        recoveryOtpAccepted("recovery-access-token");
        String resetToken = service.verifyCode(EMAIL, "123456").token();

        service.resetPassword(resetToken, "NewSecret1", "NewSecret1");

        verify(auth).updatePassword("recovery-access-token", "NewSecret1");
        verify(auth).logout("recovery-access-token", LogoutScope.LOCAL);
    }

    @Test
    void resetTokenWorksOnlyOnce() {
        recoveryOtpAccepted("recovery-access-token");
        String resetToken = service.verifyCode(EMAIL, "123456").token();
        service.resetPassword(resetToken, "NewSecret1", "NewSecret1");

        assertThatThrownBy(() -> service.resetPassword(resetToken, "NewSecret2", "NewSecret2"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_RESET_TOKEN"));
        verify(auth, times(1)).updatePassword(anyString(), anyString());
    }

    @Test
    void resetTokenExpiresAfterTtl() {
        recoveryOtpAccepted("recovery-access-token");
        String resetToken = service.verifyCode(EMAIL, "123456").token();

        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.resetPassword(resetToken, "NewSecret1", "NewSecret1"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_RESET_TOKEN"));
        verify(auth, never()).updatePassword(anyString(), anyString());
        verify(auth).logout("recovery-access-token", LogoutScope.LOCAL);
    }

    @Test
    void unknownTokenIs401() {
        assertThatThrownBy(() -> service.resetPassword("made-up", "NewSecret1", "NewSecret1"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_RESET_TOKEN"));
    }

    @Test
    void newVerificationReplacesAndRevokesPreviousRecoverySession() {
        recoveryOtpAccepted("first-recovery");
        String first = service.verifyCode(EMAIL, "123456").token();
        recoveryOtpAccepted("second-recovery");
        String second = service.verifyCode(EMAIL, "123456").token();

        verify(auth).logout("first-recovery", LogoutScope.LOCAL);
        assertThatThrownBy(() -> service.resetPassword(first, "NewSecret1", "NewSecret1"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_RESET_TOKEN"));
        service.resetPassword(second, "NewSecret1", "NewSecret1");
        verify(auth).updatePassword("second-recovery", "NewSecret1");
    }

    @Test
    void passwordRejectedBySupabaseKeepsTokenForRetry() {
        recoveryOtpAccepted("recovery-access-token");
        String resetToken = service.verifyCode(EMAIL, "123456").token();
        doThrow(new SupabaseAuthException(Reason.SAME_PASSWORD, "x"))
                .when(auth).updatePassword("recovery-access-token", "OldSecret1");

        assertThatThrownBy(() -> service.resetPassword(resetToken, "OldSecret1", "OldSecret1"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("SAME_PASSWORD"));

        service.resetPassword(resetToken, "NewSecret1", "NewSecret1");
        verify(auth).updatePassword("recovery-access-token", "NewSecret1");
    }

    @Test
    void localValidationFailsBeforeConsumingToken() {
        recoveryOtpAccepted("recovery-access-token");
        String resetToken = service.verifyCode(EMAIL, "123456").token();

        assertThatThrownBy(() -> service.resetPassword(resetToken, "short", "short")).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.resetPassword(resetToken, "NewSecret1", "NewSecret2")).isInstanceOf(ValidationFailedException.class);

        service.resetPassword(resetToken, "NewSecret1", "NewSecret1");
        verify(auth, times(1)).updatePassword("recovery-access-token", "NewSecret1");
    }

    @Test
    void purgeExpiredRevokesRecoverySessions() {
        recoveryOtpAccepted("recovery-access-token");
        service.verifyCode(EMAIL, "123456");

        clock.advance(Duration.ofMinutes(9));
        service.purgeExpired();
        verify(auth, never()).logout(anyString(), org.mockito.ArgumentMatchers.any());

        clock.advance(Duration.ofMinutes(1));
        service.purgeExpired();
        verify(auth).logout("recovery-access-token", LogoutScope.LOCAL);
    }
}
