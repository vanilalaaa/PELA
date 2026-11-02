package com.pela.shared.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockPortTest {

    @Test
    void todayUsesLearnerTimezone() {
        ClockPort clock = () -> Instant.parse("2026-10-31T18:30:00Z");

        assertThat(clock.today(ZoneId.of("UTC"))).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(clock.today(ZoneId.of("Asia/Ho_Chi_Minh"))).isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
