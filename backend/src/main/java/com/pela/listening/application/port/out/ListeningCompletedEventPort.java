package com.pela.listening.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Báo một phiên nghe vừa hoàn thành (ngay sau khi lưu). Gamification sẽ cài adapter thật: star_event với
 * {@code starsAdded} (BR-36, chỉ ghi khi > 0), streak BR-11, bộ đếm mở khoá Final Test BR-20; hiện là no-op.
 * Adapter phải idempotent theo sessionId.
 */
public interface ListeningCompletedEventPort {

    void listeningCompleted(ListeningCompleted event);

    record ListeningCompleted(UUID sessionId, UUID userId, UUID clipId, int stars, int starsAdded,
            Instant completedAt) {
    }
}
