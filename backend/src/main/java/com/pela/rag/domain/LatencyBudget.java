package com.pela.rag.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Ngân sách thời gian của cả chuỗi sinh đề (ai.question_gen_p95_budget_ms, PER-3): từ lúc nhận yêu cầu đến lúc có đề
 * để trả. Thời gian "bây giờ" truyền vào từ ClockPort nên test cố định được.
 */
public record LatencyBudget(Instant deadline) {

    public static LatencyBudget startingAt(Instant start, long budgetMillis) {
        return new LatencyBudget(start.plusMillis(budgetMillis));
    }

    /** Thời gian còn lại; không âm. */
    public Duration remaining(Instant now) {
        Duration left = Duration.between(now, deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(deadline);
    }
}
