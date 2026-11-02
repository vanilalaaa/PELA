package com.pela.rag.application.port.in;

/**
 * Tạo bù embedding cho đề Writing: đề active có embedding_model khác {@code ai.model.embedding} (embed lại), và đề
 * chưa từng kích hoạt (retired, retired_at NULL, chưa có embedding — vd seed dev) thì embed rồi chuyển active.
 * Đề do Admin retire (có retired_at) không bị đụng.
 */
public interface BackfillPromptEmbeddingsUseCase {

    BackfillResult backfill();

    /** @param stoppedByRateLimit dừng sớm vì Gemini báo quá giới hạn; lần chạy sau làm tiếp */
    record BackfillResult(int reembedded, int activated, int failed, boolean stoppedByRateLimit) {
    }
}
