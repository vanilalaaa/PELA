package com.pela.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pela.identity.domain.AccountStatus;
import com.pela.identity.domain.Role;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.ForbiddenException;
import com.pela.shared.domain.exception.PelaException;
import com.pela.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountAccessServiceTest {

    private final InMemoryUserAccounts accounts = new InMemoryUserAccounts();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T08:00:00Z"));
    private final SettingsPort settings = key -> Optional.ofNullable(
            Map.of("account.deletion_grace_days", "30").get(key));
    private final AccountAccessService service = new AccountAccessService(accounts, clock,
            InMemoryUserAccounts.standing(accounts, settings, clock));

    private static String code(Throwable t) {
        return ((PelaException) t).code();
    }

    @Test
    void activeAdminPasses() {
        var admin = accounts.add("admin@example.test", Role.ADMIN, AccountStatus.ACTIVE);

        assertThatCode(() -> service.requireActiveAdmin(admin.userId())).doesNotThrowAnyException();
    }

    @Test
    void learnerSuspendedAdminAndUnknownUserAreNotAdmins() {
        var learner = accounts.add("l@example.test", Role.LEARNER, AccountStatus.ACTIVE);
        var suspended = accounts.add("s@example.test", Role.ADMIN, AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> service.requireActiveAdmin(learner.userId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.requireActiveAdmin(suspended.userId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.requireActiveAdmin(UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminDecisionIsCachedFor60Seconds() {
        var admin = accounts.add("admin@example.test", Role.ADMIN, AccountStatus.ACTIVE);
        service.requireActiveAdmin(admin.userId());

        accounts.withStatus(admin.userId(), AccountStatus.SUSPENDED, null, "x");
        clock.advance(Duration.ofSeconds(59));
        assertThatCode(() -> service.requireActiveAdmin(admin.userId())).doesNotThrowAnyException();
        assertThat(accounts.findByIdCalls).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> service.requireActiveAdmin(admin.userId())).isInstanceOf(ForbiddenException.class);
        assertThat(accounts.findByIdCalls).isEqualTo(2);
    }

    @Test
    void activeUnverifiedAndUnknownAccountsAreUsable() {
        var active = accounts.add("a@example.test", Role.LEARNER, AccountStatus.ACTIVE);
        var unverified = accounts.add("u@example.test", Role.LEARNER, AccountStatus.UNVERIFIED);

        assertThatCode(() -> service.requireUsableAccount(active.userId())).doesNotThrowAnyException();
        assertThatCode(() -> service.requireUsableAccount(unverified.userId())).doesNotThrowAnyException();
        assertThatCode(() -> service.requireUsableAccount(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void suspendedAccountIsBlockedWithReason() {
        var learner = accounts.add("l@example.test", Role.LEARNER, AccountStatus.ACTIVE);
        Instant until = clock.now().plus(Duration.ofDays(2));
        accounts.withStatus(learner.userId(), AccountStatus.SUSPENDED, until, "Spam");

        assertThatThrownBy(() -> service.requireUsableAccount(learner.userId()))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(e -> {
                    assertThat(code(e)).isEqualTo("ACCOUNT_SUSPENDED");
                    assertThat(((PelaException) e).details())
                            .isEqualTo(Map.of("reason", "Spam", "suspendedUntil", until.toString()));
                });
    }

    @Test
    void pendingDeletionIsBlockedWithDeletionDate() {
        var learner = accounts.add("l@example.test", Role.LEARNER, AccountStatus.ACTIVE);
        accounts.pendingDeletion(learner.userId(), Instant.parse("2026-10-20T00:00:00Z"));

        assertThatThrownBy(() -> service.requireUsableAccount(learner.userId()))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(e -> {
                    assertThat(code(e)).isEqualTo("ACCOUNT_PENDING_DELETION");
                    assertThat(((PelaException) e).details())
                            .isEqualTo(Map.of("deletionScheduledAt", "2026-11-19T00:00:00Z"));
                });
    }

    @Test
    void expiredSuspensionIsReleasedAndAccountPasses() {
        var learner = accounts.add("l@example.test", Role.LEARNER, AccountStatus.ACTIVE);
        accounts.withStatus(learner.userId(), AccountStatus.SUSPENDED, clock.now().plusSeconds(30), "Short ban");
        assertThatThrownBy(() -> service.requireUsableAccount(learner.userId())).isInstanceOf(ForbiddenException.class);

        // Snapshot vẫn trong cache 60s nhưng quyết định dựa trên thời gian: hết hạn là cho qua và mở khoá
        clock.advance(Duration.ofSeconds(30));
        assertThatCode(() -> service.requireUsableAccount(learner.userId())).doesNotThrowAnyException();
        assertThat(accounts.get(learner.userId()).status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void statusIsCachedFor60Seconds() {
        var learner = accounts.add("l@example.test", Role.LEARNER, AccountStatus.ACTIVE);
        service.requireUsableAccount(learner.userId());

        accounts.withStatus(learner.userId(), AccountStatus.SUSPENDED, null, "Abuse");
        clock.advance(Duration.ofSeconds(59));
        assertThatCode(() -> service.requireUsableAccount(learner.userId())).doesNotThrowAnyException();

        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> service.requireUsableAccount(learner.userId()))
                .satisfies(e -> assertThat(code(e)).isEqualTo("ACCOUNT_SUSPENDED"));
    }
}
