package com.pela.rag.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Đọc lại đề AI đã phát cho đúng learner (khi nộp bài với promptSource = ai_generated). */
public interface GetServedPromptUseCase {

    /** Rỗng nếu đề không tồn tại hoặc chưa từng được phát cho {@code userId} (không cho dùng đề của người khác). */
    Optional<ServedPrompt> servedTo(UUID userId, UUID cacheId);
}
