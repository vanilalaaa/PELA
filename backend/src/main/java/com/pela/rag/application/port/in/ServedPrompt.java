package com.pela.rag.application.port.in;

import java.util.UUID;

/**
 * Đề Writing do AI tạo đã phát cho learner (nằm ở rag_question_cache, không bao giờ vào question_bank, BR-25).
 *
 * @param cacheId   rag_question_cache.cache_id; learner gửi lại khi nộp bài (writing_submission.rag_cache_id)
 * @param taskType  email | paragraph | essay
 */
public record ServedPrompt(UUID cacheId, String promptText, String taskType, int minWords, int maxWords) {
}
