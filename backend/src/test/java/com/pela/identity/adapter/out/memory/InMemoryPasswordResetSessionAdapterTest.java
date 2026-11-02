package com.pela.identity.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.identity.application.port.out.PasswordResetSessionPort.PendingPasswordReset;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryPasswordResetSessionAdapterTest {

    private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");

    private final InMemoryPasswordResetSessionAdapter store = new InMemoryPasswordResetSessionAdapter();

    @Test
    void takeReturnsSessionOnlyOnce() {
        var reset = new PendingPasswordReset(UUID.randomUUID(), "recovery", T0.plusSeconds(600));
        store.save("token-a", reset);

        assertThat(store.take("token-a")).contains(reset);
        assertThat(store.take("token-a")).isEmpty();
        assertThat(store.take("other")).isEmpty();
    }

    @Test
    void newSessionForSameUserReplacesAndReturnsPrevious() {
        UUID user = UUID.randomUUID();
        var first = new PendingPasswordReset(user, "first", T0.plusSeconds(600));
        var second = new PendingPasswordReset(user, "second", T0.plusSeconds(600));

        assertThat(store.save("token-1", first)).isEmpty();
        assertThat(store.save("token-2", second)).contains(first);
        assertThat(store.take("token-1")).isEmpty();
        assertThat(store.take("token-2")).contains(second);
    }

    @Test
    void savingSameTokenAgainKeepsIt() {
        var reset = new PendingPasswordReset(UUID.randomUUID(), "recovery", T0.plusSeconds(600));
        store.save("token-a", reset);
        store.take("token-a");

        assertThat(store.save("token-a", reset)).isEmpty();
        assertThat(store.take("token-a")).contains(reset);
    }

    @Test
    void removeExpiredReturnsOnlyExpired() {
        var expired = new PendingPasswordReset(UUID.randomUUID(), "old", T0);
        var alive = new PendingPasswordReset(UUID.randomUUID(), "new", T0.plusSeconds(60));
        store.save("t-old", expired);
        store.save("t-new", alive);

        assertThat(store.removeExpired(T0)).containsExactly(expired);
        assertThat(store.take("t-old")).isEmpty();
        assertThat(store.take("t-new")).contains(alive);
    }

    @Test
    void toStringNeverShowsRecoveryToken() {
        var reset = new PendingPasswordReset(UUID.randomUUID(), "secret-recovery-token", T0);

        assertThat(reset.toString()).doesNotContain("secret-recovery-token");
    }
}
