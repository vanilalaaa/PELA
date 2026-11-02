package com.pela.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LockoutPolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");
    private final LockoutPolicy policy = new LockoutPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15));

    @Test
    void fifthFailureWithinWindowLocksFor15Minutes() {
        LoginState state = LoginState.CLEAN;
        for (int i = 0; i < 4; i++) {
            state = policy.onFailure(state, T0.plusSeconds(60L * i));
        }
        assertThat(state.failedCount()).isEqualTo(4);
        assertThat(state.windowStartedAt()).isEqualTo(T0);
        assertThat(state.lockedUntil()).isNull();

        Instant fifth = T0.plusSeconds(600);
        state = policy.onFailure(state, fifth);

        assertThat(state.lockedUntil()).isEqualTo(fifth.plus(Duration.ofMinutes(15)));
        assertThat(state.failedCount()).isZero();
        assertThat(state.isLockedAt(fifth.plusSeconds(899))).isTrue();
        assertThat(state.isLockedAt(fifth.plusSeconds(900))).isFalse();
    }

    @Test
    void failureAfterWindowStartsNewCount() {
        LoginState state = LoginState.CLEAN;
        for (int i = 0; i < 4; i++) {
            state = policy.onFailure(state, T0);
        }

        state = policy.onFailure(state, T0.plus(Duration.ofMinutes(15)));

        assertThat(state.failedCount()).isEqualTo(1);
        assertThat(state.windowStartedAt()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
        assertThat(state.lockedUntil()).isNull();
    }

    @Test
    void failureWhileLockedKeepsLock() {
        LoginState locked = new LoginState(0, null, T0.plusSeconds(300));

        assertThat(policy.onFailure(locked, T0)).isEqualTo(locked);
    }

    @Test
    void successClearsEverything() {
        assertThat(policy.onSuccess()).isEqualTo(LoginState.CLEAN);
    }
}
