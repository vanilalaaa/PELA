package com.pela.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthException.Reason;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import com.pela.shared.domain.exception.PelaException;
import com.pela.shared.domain.exception.TooManyRequestsException;
import com.pela.shared.domain.exception.UnauthorizedException;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordRecoveryServiceTest {

    private final SupabaseAuthPort auth = mock(SupabaseAuthPort.class);
    private final PasswordRecoveryService service = new PasswordRecoveryService(auth);

    @Test
    void requestResetNormalizesEmail() {
        service.requestReset(" Learner@Example.test ");

        verify(auth).resetPasswordForEmail("learner@example.test");
    }

    @Test
    void requestResetHidesUnknownEmailButReportsRateLimit() {
        doThrow(new SupabaseAuthException(Reason.USER_NOT_FOUND, "x")).when(auth).resetPasswordForEmail("a@example.test");
        service.requestReset("a@example.test");

        doThrow(new SupabaseAuthException(Reason.RATE_LIMITED, "x")).when(auth).resetPasswordForEmail("b@example.test");
        assertThatThrownBy(() -> service.requestReset("b@example.test")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void verifyCodeReturnsResetToken() {
        when(auth.verifyRecoveryOtp("a@example.test", "123456"))
                .thenReturn(new SupabaseSession("recovery-token", "r", 3600, "bearer", UUID.randomUUID(), true));

        var token = service.verifyCode("a@example.test", "123456");

        assertThat(token.token()).isEqualTo("recovery-token");
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void wrongOrExpiredCodeIs422InvalidOtp() {
        when(auth.verifyRecoveryOtp(anyString(), anyString()))
                .thenThrow(new SupabaseAuthException(Reason.INVALID_OTP, "x"));

        assertThatThrownBy(() -> service.verifyCode("a@example.test", "000000"))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(e -> assertThat(((PelaException) e).code()).isEqualTo("INVALID_OTP"));
    }

    @Test
    void resetPasswordUpdatesThenEndsRecoverySession() {
        service.resetPassword("recovery-token", "NewSecret1", "NewSecret1");

        verify(auth).updatePassword("recovery-token", "NewSecret1");
        verify(auth).logout("recovery-token", LogoutScope.LOCAL);
    }

    @Test
    void resetPasswordValidatesPolicyAndConfirmation() {
        assertThatThrownBy(() -> service.resetPassword("t", "short", "short")).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.resetPassword("t", "NewSecret1", "NewSecret2")).isInstanceOf(ValidationFailedException.class);
        verify(auth, never()).updatePassword(anyString(), anyString());
    }

    @Test
    void expiredResetTokenIs401() {
        doThrow(new SupabaseAuthException(Reason.INVALID_ACCESS_TOKEN, "x")).when(auth).updatePassword("old", "NewSecret1");

        assertThatThrownBy(() -> service.resetPassword("old", "NewSecret1", "NewSecret1"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(e -> assertThat(((PelaException) e).code()).isEqualTo("INVALID_RESET_TOKEN"));
    }
}
