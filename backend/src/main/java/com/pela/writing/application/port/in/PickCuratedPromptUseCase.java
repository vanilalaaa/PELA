package com.pela.writing.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Đề Writing có sẵn trong kho cho một topic + level: phát khi không tạo được đề AI (BR-7) và gợi ý cho learner. */
public interface PickCuratedPromptUseCase {

    /** Đề chưa làm (ngẫu nhiên); làm hết thì đề làm lâu nhất. Rỗng nếu kho không có đề active cho topic + level. */
    Optional<CuratedPrompt> pick(UUID userId, String topic, String cefrLevel);

    /** Gợi ý tối đa {@code limit} đề: chưa làm trước, rồi đề làm lâu nhất. */
    List<CuratedPrompt> suggestions(UUID userId, String topic, String cefrLevel, int limit);

    /** @param done learner đã nộp bài với đề này */
    record CuratedPrompt(UUID questionId, String text, String taskType, Integer minWords, Integer maxWords,
            boolean done) {
    }
}
