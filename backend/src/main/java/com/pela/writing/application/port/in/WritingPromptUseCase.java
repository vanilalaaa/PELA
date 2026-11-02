package com.pela.writing.application.port.in;

import com.pela.writing.application.port.in.PickCuratedPromptUseCase.CuratedPrompt;
import java.util.List;
import java.util.UUID;

/** UC-24 từ phía learner (UC-10 "Để AI tạo đề mới"): đề AI nếu được, nếu không thì đề có sẵn (BR-7). */
public interface WritingPromptUseCase {

    /**
     * Ngân sách ai.question_gen_p95_budget_ms tính cho cả chuỗi. Không bao giờ thất bại vì AI: mọi sự cố đều trả đề có
     * sẵn. Chỉ ném lỗi khi learner chưa có trình độ, topic sai, hoặc kho không có đề nào cho topic + level.
     */
    PromptChoice generate(UUID userId, String topic, String taskType);

    /** Đề có sẵn gợi ý cho topic ở trình độ hiện tại của learner. */
    List<CuratedPrompt> suggestions(UUID userId, String topic, int limit);

    /**
     * @param promptSource   ai_generated | bank; nộp bài dùng đúng giá trị này
     * @param ragCacheId     có khi promptSource = ai_generated: gửi lại khi nộp bài
     * @param questionId     có khi promptSource = bank: gửi lại khi nộp bài (promptQuestionId)
     * @param cacheHit       đề AI lấy từ cache chứ không phải mới tạo
     * @param fallbackReason có khi đề bank được phát vì không tạo được đề AI (BELOW_THRESHOLD, TIMEOUT, …)
     */
    record PromptChoice(String promptSource, UUID ragCacheId, UUID questionId, String promptText, String taskType,
            Integer minWords, Integer maxWords, boolean cacheHit, String fallbackReason) {
    }
}
