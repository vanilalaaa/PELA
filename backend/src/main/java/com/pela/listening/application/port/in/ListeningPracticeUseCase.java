package com.pela.listening.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** UC-11 Practice Listening: chọn topic → bài nghe (theo order_index, không gợi ý), nghe, nộp đáp án theo lô. */
public interface ListeningPracticeUseCase {

    record TopicItem(String topic, int clipCount) {
    }

    /** @param bestStars sao cao nhất learner từng đạt ở bài này; null = chưa làm */
    record ClipItem(UUID clipId, String title, int orderIndex, Integer durationSec, int questionCount,
            Integer bestStars) {
    }

    /** Câu hỏi trước khi nộp: không có đáp án và giải thích. */
    record QuestionView(UUID questionId, String type, String text, List<String> options) {
    }

    /** Chi tiết bài nghe trước khi nộp: URL audio ký tạm, câu hỏi; không có transcript (Listening.Answer.Explain). */
    record ClipView(UUID clipId, String title, String topic, String cefrLevel, Integer durationSec, String audioUrl,
            Instant audioUrlExpiresAt, List<QuestionView> questions, Integer bestStars) {
    }

    record Answer(UUID questionId, String selectedAnswer) {
    }

    /** @param sessionId do client sinh; gửi lại cùng id trả lại đúng kết quả cũ */
    record SubmitCommand(UUID sessionId, UUID clipId, Instant startedAt, List<Answer> answers) {
    }

    record QuestionResult(UUID questionId, String selectedAnswer, String correctAnswer, boolean correct,
            String explanation) {
    }

    record TranscriptLine(String speaker, String en, String vi) {
    }

    /**
     * @param stars        sao của phiên (BR-16)
     * @param previousBest sao tốt nhất trước phiên này (0 nếu chưa làm)
     * @param starsAdded   phần được cộng vào star_total (BR-36)
     */
    record SessionResult(UUID sessionId, UUID clipId, int correctCount, int totalQuestions, int stars,
            int previousBest, int starsAdded, Instant completedAt, List<QuestionResult> results,
            List<TranscriptLine> transcript) {
    }

    List<TopicItem> topics(String level);

    List<ClipItem> clips(UUID userId, String topic, String level);

    ClipView clip(UUID userId, UUID clipId);

    SessionResult submit(UUID userId, SubmitCommand command);
}
