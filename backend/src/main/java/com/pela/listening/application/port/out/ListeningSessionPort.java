package com.pela.listening.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bảng listening_session + listening_attempt. */
public interface ListeningSessionPort {

    record Attempt(UUID attemptId, UUID questionId, String selectedAnswer, boolean correct) {
    }

    record StoredSession(UUID sessionId, UUID userId, UUID clipId, int totalQuestions, int correctCount, int stars,
            Instant completedAt, List<Attempt> attempts) {
    }

    record NewSession(UUID sessionId, UUID userId, UUID clipId, int totalQuestions, int correctCount, int stars,
            Instant startedAt, Instant completedAt, List<Attempt> attempts) {
    }

    /**
     * @param created      false = sessionId đã có (gửi lại / gửi trùng đồng thời): không ghi gì
     * @param previousBest sao tốt nhất của learner ở bài này trước phiên này (0 nếu chưa có)
     */
    record SaveOutcome(boolean created, int previousBest) {
    }

    Optional<StoredSession> find(UUID sessionId);

    /** Sao tốt nhất theo bài nghe; bài chưa làm không có trong map. */
    Map<UUID, Integer> bestStars(UUID userId, Collection<UUID> clipIds);

    /** Sao tốt nhất của các phiên hoàn thành trước {@code before} (không tính chính phiên đó). */
    int bestStarsBefore(UUID userId, UUID clipId, UUID excludeSessionId, Instant before);

    /**
     * Ghi phiên + đáp án trong một transaction, khoá theo (learner, bài nghe) để hai phiên đồng thời không cùng tính
     * phần tăng BR-36 từ một mốc cũ.
     */
    SaveOutcome save(NewSession session);
}
