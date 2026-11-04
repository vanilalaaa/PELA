package com.pela.rag.application.port.out;

import com.pela.rag.application.port.in.ServedPrompt;
import com.pela.rag.domain.GeneratedPrompt;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** rag_question_cache + rag_cache_serve (BR-24, BR-25). */
public interface PromptCachePort {

    /**
     * RAG.Cache.Lookup: entry active, chưa hết hạn, cùng topic + level (+ dạng bài nếu có), similarity truy vấn >=
     * ngưỡng, và chưa phát cho learner này từ {@code notServedSince}. Gần nhất trước.
     */
    Optional<ServedPrompt> findHit(CacheLookup lookup);

    /** Lưu đề mới vừa sinh và ghi luôn lần phát đầu tiên cho {@code userId} (cùng transaction). */
    void storeAndServe(NewCacheEntry entry, UUID userId, Instant now);

    /** Ghi một lần phát từ cache: tăng hit_count, cập nhật last_hit_at, thêm dòng rag_cache_serve. */
    void serveHit(UUID cacheId, UUID userId, Instant now);

    /** Số đề AI đã phát cho learner trong [from, to): cả cache hit và đề mới tạo (RAG.Limit.Daily). */
    int countServedBetween(UUID userId, Instant from, Instant to);

    Optional<ServedPrompt> servedTo(UUID userId, UUID cacheId);

    int expireBySourceTemplate(UUID questionId);

    int expireDue(Instant now);

    record CacheLookup(float[] queryEmbedding, String topic, String cefrLevel, Optional<String> taskType,
            double minSimilarity, UUID userId, Instant notServedSince, Instant now) {
    }

    /**
     * @param taskType         dạng bài đã chuẩn hoá (email|paragraph|essay): cùng topic + level là khoá cache (BR-24)
     * @param sourceTemplateId đề kho Top-1 (BR-25: retire đề này thì cache expired)
     */
    record NewCacheEntry(UUID cacheId, float[] queryEmbedding, String topic, String cefrLevel, String taskType,
            GeneratedPrompt prompt, UUID sourceTemplateId, Instant expiresAt) {
    }
}
