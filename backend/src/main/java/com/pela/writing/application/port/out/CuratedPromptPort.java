package com.pela.writing.application.port.out;

import com.pela.writing.domain.PromptCandidate;
import java.util.List;
import java.util.UUID;

/** Đề Writing đang dùng của kho (question_bank) và lịch sử làm của learner (writing_submission). */
public interface CuratedPromptPort {

    /** Mọi đề active của topic + level kèm lần làm gần nhất của learner; theo thứ tự tạo (tối đa vài trăm đề). */
    List<PromptCandidate> candidates(UUID userId, String topic, String cefrLevel);
}
