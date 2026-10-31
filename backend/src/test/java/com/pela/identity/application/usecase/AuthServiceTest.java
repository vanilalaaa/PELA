package com.pela.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pela.identity.application.port.in.AuthTokens;
import com.pela.identity.application.port.in.RegisterUseCase.RegisterCommand;
import com.pela.identity.application.port.out.SupabaseAuthException;
import com.pela.identity.application.port.out.SupabaseAuthException.Reason;
import com.pela.identity.application.port.out.SupabaseAuthPort;
import com.pela.identity.application.port.out.SupabaseAuthPort.LogoutScope;
import com.pela.identity.application.port.out.SupabaseSession;
import com.pela.identity.domain.AccountStatus;
import com.pela.identity.domain.Role;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.application.port.out.SystemSettingPort;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.ForbiddenException;
import com.pela.shared.domain.exception.LockedException;
import com.pela.shared.domain.exception.PelaException;
import com.pela.shared.domain.exception.UnauthorizedException;
import com.pela.shared.domain.exception.ValidationFailedException;
import com.pela.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final String EMAIL = "learner@example.test";
    private static final String PASSWORD = "Secret123";

    private final SupabaseAuthPort auth = mock(SupabaseAuthPort.class);
    private final InMemoryUserAccounts accounts = new InMemoryUserAccounts();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T08:00:00Z"));
    private final SystemSettingPort settings = key -> Optional.ofNullable(Map.of(
            "auth.lockout_max_attempts", "5", "auth.lockout_minutes", "15").get(key));
    private final AuthService service = new AuthService(auth, accounts, settings, clock);

    private UserAccount learner;

    @BeforeEach
    void setUp() {
        learner = accounts.add(EMAIL, Role.LEARNER, AccountStatus.ACTIVE);
    }

    private SupabaseSession session(UUID userId, boolean emailConfirmed) {
        return new SupabaseSession("access-token", "refresh-token", 604800, "bearer", userId, emailConfirmed);
    }

    private void wrongPassword() {
        when(auth.signInWithPassword(EMAIL, "wrong"))
                .thenThrow(new SupabaseAuthException(Reason.INVALID_CREDENTIALS, "invalid"));
    }

    private static String code(Throwable t) {
        return ((PelaException) t).code();
    }

    @Nested
    class Login {

        @Test
        void successReturnsTokensAndResetsCounter() {
            wrongPassword();
            assertThatThrownBy(() -> service.login(EMAIL, "wrong")).isInstanceOf(UnauthorizedException.class);
            when(auth.signInWithPassword(EMAIL, PASSWORD)).thenReturn(session(learner.userId(), true));

            AuthTokens tokens = service.login("  Learner@Example.TEST ", PASSWORD);

            assertThat(tokens.accessToken()).isEqualTo("access-token");
            assertThat(tokens.expiresInSeconds()).isEqualTo(604800);
            assertThat(accounts.get(learner.userId()).loginState().failedCount()).isZero();
        }

        @Test
        void unknownEmailIsInvalidCredentialsWithoutCallingSupabase() {
            assertThatThrownBy(() -> service.login("nobody@example.test", PASSWORD))
                    .isInstanceOf(UnauthorizedException.class)
                    .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_CREDENTIALS"));
            verify(auth, never()).signInWithPassword(anyString(), anyString());
        }

        @Test
        void fifthWrongPasswordLocksAccount() {
            wrongPassword();
            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> service.login(EMAIL, "wrong"))
                        .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_CREDENTIALS"));
                clock.advance(Duration.ofMinutes(1));
            }

            assertThatThrownBy(() -> service.login(EMAIL, "wrong"))
                    .isInstanceOf(LockedException.class)
                    .satisfies(e -> {
                        assertThat(code(e)).isEqualTo("ACCOUNT_LOCKED");
                        assertThat(((PelaException) e).details())
                                .isEqualTo(Map.of("lockedUntil", "2026-11-01T08:19:00Z"));
                    });
        }

        @Test
        void lockedAccountIsRejectedBeforeAskingSupabase() {
            wrongPassword();
            for (int i = 0; i < 5; i++) {
                try {
                    service.login(EMAIL, "wrong");
                } catch (PelaException ignored) {
                    // đếm đủ 5 lần
                }
            }
            when(auth.signInWithPassword(EMAIL, PASSWORD)).thenReturn(session(learner.userId(), true));
            clock.advance(Duration.ofMinutes(14));

            assertThatThrownBy(() -> service.login(EMAIL, PASSWORD)).isInstanceOf(LockedException.class);
            verify(auth, never()).signInWithPassword(EMAIL, PASSWORD);

            clock.advance(Duration.ofMinutes(1));
            assertThat(service.login(EMAIL, PASSWORD).accessToken()).isEqualTo("access-token");
        }

        @Test
        void failuresOutsideWindowDoNotLock() {
            wrongPassword();
            for (int i = 0; i < 8; i++) {
                assertThatThrownBy(() -> service.login(EMAIL, "wrong")).isInstanceOf(UnauthorizedException.class);
                clock.advance(Duration.ofMinutes(4)); // 4 lần mỗi cửa sổ 15 phút
            }
        }

        @Test
        void suspendedAccountGets403WithReasonAndSessionIsRevoked() {
            Instant until = clock.now().plus(Duration.ofDays(3));
            accounts.withStatus(learner.userId(), AccountStatus.SUSPENDED, until, "Spam");
            when(auth.signInWithPassword(EMAIL, PASSWORD)).thenReturn(session(learner.userId(), true));

            assertThatThrownBy(() -> service.login(EMAIL, PASSWORD))
                    .isInstanceOf(ForbiddenException.class)
                    .satisfies(e -> {
                        assertThat(code(e)).isEqualTo("ACCOUNT_SUSPENDED");
                        assertThat(((PelaException) e).details())
                                .isEqualTo(Map.of("reason", "Spam", "suspendedUntil", until.toString()));
                    });
            verify(auth).logout("access-token", LogoutScope.LOCAL);
        }

        @Test
        void expiredSuspensionIsLiftedOnLogin() {
            accounts.withStatus(learner.userId(), AccountStatus.SUSPENDED, clock.now().minusSeconds(1), "Old");
            when(auth.signInWithPassword(EMAIL, PASSWORD)).thenReturn(session(learner.userId(), true));

            service.login(EMAIL, PASSWORD);

            assertThat(accounts.get(learner.userId()).status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void unverifiedWithoutConfirmedEmailGets403() {
            accounts.withStatus(learner.userId(), AccountStatus.UNVERIFIED, null, null);
            when(auth.signInWithPassword(EMAIL, PASSWORD)).thenReturn(session(learner.userId(), false));

            assertThatThrownBy(() -> service.login(EMAIL, PASSWORD))
                    .satisfies(e -> assertThat(code(e)).isEqualTo("EMAIL_NOT_VERIFIED"));
            verify(auth).logout("access-token", LogoutScope.LOCAL);
        }

        @Test
        void supabaseEmailNotConfirmedGets403() {
            when(auth.signInWithPassword(EMAIL, PASSWORD))
                    .thenThrow(new SupabaseAuthException(Reason.EMAIL_NOT_CONFIRMED, "not confirmed"));

            assertThatThrownBy(() -> service.login(EMAIL, PASSWORD))
                    .isInstanceOf(ForbiddenException.class)
                    .satisfies(e -> assertThat(code(e)).isEqualTo("EMAIL_NOT_VERIFIED"));
            assertThat(accounts.get(learner.userId()).loginState().failedCount()).isZero();
        }

        @Test
        void unverifiedBecomesActiveOnceSupabaseConfirmedEmail() {
            accounts.withStatus(learner.userId(), AccountStatus.UNVERIFIED, null, null);
            when(auth.signInWithPassword(EMAIL, PASSWORD)).thenReturn(session(learner.userId(), true));

            service.login(EMAIL, PASSWORD);

            assertThat(accounts.get(learner.userId()).status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void providerOutageIs503AndNotCountedAsFailure() {
            when(auth.signInWithPassword(EMAIL, PASSWORD))
                    .thenThrow(new SupabaseAuthException(Reason.UNAVAILABLE, "down"));

            assertThatThrownBy(() -> service.login(EMAIL, PASSWORD))
                    .satisfies(e -> assertThat(code(e)).isEqualTo("AUTH_PROVIDER_UNAVAILABLE"));
            assertThat(accounts.get(learner.userId()).loginState().failedCount()).isZero();
        }
    }

    @Nested
    class Register {

        @Test
        void createsUnverifiedAccount() {
            UUID newId = UUID.randomUUID();
            when(auth.signUp("new@example.test", "Secret123", "Lan Anh")).thenReturn(newId);

            var result = service.register(new RegisterCommand(" Lan Anh ", "New@Example.test", "Secret123", "Secret123"));

            assertThat(result.userId()).isEqualTo(newId);
            assertThat(result.email()).isEqualTo("new@example.test");
            assertThat(result.status()).isEqualTo("unverified");
        }

        @Test
        void weakPasswordIs422WithFieldDetails() {
            assertThatThrownBy(() -> service.register(new RegisterCommand("A", "a@example.test", "weak", "weak")))
                    .isInstanceOf(ValidationFailedException.class)
                    .satisfies(e -> assertThat(((PelaException) e).details().toString()).contains("password"));
            verify(auth, never()).signUp(anyString(), anyString(), anyString());
        }

        @Test
        void confirmationMismatchIs422() {
            assertThatThrownBy(() -> service.register(
                    new RegisterCommand("A", "a@example.test", "Secret123", "Secret124")))
                    .isInstanceOf(ValidationFailedException.class)
                    .satisfies(e -> assertThat(((PelaException) e).details().toString()).contains("confirmPassword"));
        }

        @Test
        void existingEmailIs409() {
            assertThatThrownBy(() -> service.register(new RegisterCommand("A", EMAIL, "Secret123", "Secret123")))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(e -> assertThat(code(e)).isEqualTo("EMAIL_ALREADY_REGISTERED"));
        }

        @Test
        void supabaseDuplicateIsAlso409() {
            when(auth.signUp(anyString(), anyString(), anyString()))
                    .thenThrow(new SupabaseAuthException(Reason.USER_ALREADY_EXISTS, "exists"));

            assertThatThrownBy(() -> service.register(
                    new RegisterCommand("A", "other@example.test", "Secret123", "Secret123")))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class Session {

        @Test
        void refreshReturnsNewTokens() {
            when(auth.refresh("refresh-token")).thenReturn(session(learner.userId(), true));

            assertThat(service.refresh("refresh-token").refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        void refreshForSuspendedAccountIsRejected() {
            accounts.withStatus(learner.userId(), AccountStatus.SUSPENDED, null, "Abuse");
            when(auth.refresh("refresh-token")).thenReturn(session(learner.userId(), true));

            assertThatThrownBy(() -> service.refresh("refresh-token"))
                    .satisfies(e -> assertThat(code(e)).isEqualTo("ACCOUNT_SUSPENDED"));
            verify(auth).logout("access-token", LogoutScope.LOCAL);
        }

        @Test
        void invalidRefreshTokenIs401() {
            when(auth.refresh("bad")).thenThrow(new SupabaseAuthException(Reason.INVALID_REFRESH_TOKEN, "bad"));

            assertThatThrownBy(() -> service.refresh("bad"))
                    .isInstanceOf(UnauthorizedException.class)
                    .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_REFRESH_TOKEN"));
        }

        @Test
        void logoutScopes() {
            service.logout("t1", false);
            service.logout("t2", true);

            verify(auth).logout("t1", LogoutScope.LOCAL);
            verify(auth).logout("t2", LogoutScope.GLOBAL);
        }

        @Test
        void logoutOfAlreadyExpiredSessionSucceeds() {
            org.mockito.Mockito.doThrow(new SupabaseAuthException(Reason.INVALID_ACCESS_TOKEN, "gone"))
                    .when(auth).logout(any(), any());

            service.logout("t", false);
        }
    }
}
