package com.pela.writing.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Đề Writing trong kho (question_bank, question_purpose = writing_prompt). */
public interface WritingPromptPort {

    /** Đề đang dùng (status active) theo id. */
    Optional<BankPrompt> activePrompt(UUID questionId);

    /**
     * @param minWords   payload.min_words; null nếu đề không giới hạn hẹp hơn
     * @param rubricHint payload.rubric_hint — scoring note cho task_achievement
     */
    record BankPrompt(UUID questionId, String text, Integer minWords, Integer maxWords, String rubricHint) {
    }
}
