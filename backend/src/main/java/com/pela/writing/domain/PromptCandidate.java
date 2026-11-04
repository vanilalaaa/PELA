package com.pela.writing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Đề Writing đang dùng trong kho (cùng topic + level) kèm lần làm gần nhất của learner.
 *
 * @param taskType   email | paragraph | essay; null nếu không ghi
 * @param lastDoneAt lần nộp gần nhất của learner với đề này (không tính nháp); null = chưa làm
 */
public record PromptCandidate(UUID questionId, String text, String taskType, Integer minWords, Integer maxWords,
        Instant lastDoneAt) {

    public boolean done() {
        return lastDoneAt != null;
    }
}
