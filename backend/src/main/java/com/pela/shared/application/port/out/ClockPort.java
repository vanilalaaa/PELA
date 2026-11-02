package com.pela.shared.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Nguồn thời gian duy nhất cho domain và use case. Không gọi {@code Instant.now()} /
 * {@code LocalDate.now()} trực tiếp (ArchitectureTest kiểm tra), để test cố định được thời gian.
 */
public interface ClockPort {

    Instant now();

    /** Ngày hiện tại theo múi giờ của learner (BR-5, BR-11, BR-12). */
    default LocalDate today(ZoneId zone) {
        return LocalDate.ofInstant(now(), zone);
    }
}
