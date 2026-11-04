package com.pela.rag.application.port.out;

import com.pela.rag.domain.FallbackReason;
import java.time.Instant;
import java.util.UUID;

/** rag_generation_log: thống kê tỉ lệ fallback, cache hit và độ trễ. Không lưu nội dung đề hay truy vấn. */
public interface GenerationLogPort {

    void record(Entry entry);

    /**
     * @param outcome        generated | cache_hit | fallback
     * @param reason         chỉ có khi outcome = fallback
     * @param top1Similarity null khi chưa truy hồi (hết lượt, lỗi embedding, cache hit)
     */
    record Entry(UUID userId, String topic, String cefrLevel, String outcome, FallbackReason reason,
            Double top1Similarity, int latencyMs, Instant createdAt) {
    }
}
