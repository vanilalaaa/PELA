package com.pela.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pela.identity.domain.AccountStatus;
import com.pela.identity.domain.Role;
import com.pela.shared.domain.exception.ForbiddenException;
import com.pela.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminAccessServiceTest {

    private final InMemoryUserAccounts accounts = new InMemoryUserAccounts();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-01T08:00:00Z"));
    private final AdminAccessService service = new AdminAccessService(accounts, clock);

    @Test
    void activeAdminPasses() {
        var admin = accounts.add("admin@example.test", Role.ADMIN, AccountStatus.ACTIVE);

        assertThatCode(() -> service.requireActiveAdmin(admin.userId())).doesNotThrowAnyException();
    }

    @Test
    void learnerSuspendedAdminAndUnknownUserAreForbidden() {
        var learner = accounts.add("l@example.test", Role.LEARNER, AccountStatus.ACTIVE);
        var suspended = accounts.add("s@example.test", Role.ADMIN, AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> service.requireActiveAdmin(learner.userId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.requireActiveAdmin(suspended.userId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.requireActiveAdmin(UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void decisionIsCachedFor60Seconds() {
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
}
