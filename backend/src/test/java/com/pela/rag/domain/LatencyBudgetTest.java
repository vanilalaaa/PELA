package com.pela.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LatencyBudgetTest {

    private static final Instant START = Instant.parse("2026-11-03T10:00:00Z");

    @Test
    void remainingShrinksAsTimePassesAndNeverGoesNegative() {
        LatencyBudget budget = LatencyBudget.startingAt(START, 4000);

        assertThat(budget.remaining(START)).isEqualTo(Duration.ofMillis(4000));
        assertThat(budget.remaining(START.plusMillis(1500))).isEqualTo(Duration.ofMillis(2500));
        assertThat(budget.remaining(START.plusMillis(9000))).isEqualTo(Duration.ZERO);
    }

    @Test
    void expiresExactlyAtTheDeadline() {
        LatencyBudget budget = LatencyBudget.startingAt(START, 4000);

        assertThat(budget.isExpired(START.plusMillis(3999))).isFalse();
        assertThat(budget.isExpired(START.plusMillis(4000))).isTrue();
    }
}
