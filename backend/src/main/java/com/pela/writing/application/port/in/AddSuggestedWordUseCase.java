package com.pela.writing.application.port.in;

import java.util.UUID;

/** Writing.ReviewIntegration.AddWord (BR-30): learner bấm "Thêm vào ôn tập" cho một từ đáng học. */
public interface AddSuggestedWordUseCase {

    /** Idempotent: từ đã thêm (hoặc đã tự thêm theo BR-9) trả lại kết quả cũ. */
    AddedWord add(UUID userId, UUID suggestionId);

    record AddedWord(UUID suggestionId, String status, UUID vocabId, UUID reviewCardId) {
    }
}
