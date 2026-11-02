package com.pela.rag.evaluation;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PER-3 / PER-9: độ trễ P95 của pipeline sinh đề Writing (UC-24). Chưa chạy được vì chưa có use case sinh đề và cache
 * (RAG.Generate, RAG.Cache). Khi có UC-24 (P7):
 * <ul>
 *   <li>generate: gọi use case sinh đề cho từng truy vấn on_topic trong queries.csv, đo thời gian end-to-end gồm cả
 *       fallback BR-7; P95 ({@link EvalStats#percentile}) ≤ 4 s.</li>
 *   <li>cache hit: tạo trước bản cache (transaction rollback), đo nhánh RAG.Cache.Lookup KHÔNG tính bước embed truy
 *       vấn; P95 ≤ 300 ms.</li>
 * </ul>
 * Ghi build/evaluation/rag/latency.csv (case, branch, millis). Xem docs/backlog.md (P7).
 */
@Tag("external")
@Disabled("Chờ UC-24 (P7)")
class RagLatencyTest {

    @Test
    void generateP95IsWithin4Seconds() {
        throw new UnsupportedOperationException("Chờ UC-24 (P7)");
    }

    @Test
    void cacheHitP95IsWithin300MillisExcludingEmbedding() {
        throw new UnsupportedOperationException("Chờ UC-24 (P7)");
    }
}
