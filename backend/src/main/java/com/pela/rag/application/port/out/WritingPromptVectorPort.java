package com.pela.rag.application.port.out;

import com.pela.rag.application.port.in.SimilarWritingPrompt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Vector của đề Writing trong question_bank (pgvector, idx_qb_embedding_hnsw). */
public interface WritingPromptVectorPort {

    /**
     * RAG.Retrieve: Top-K đề Writing active cùng topic + cefr_level, gần nhất trước. Lọc xong còn ít hơn K thì HNSW
     * quét tiếp (iterative scan, RAG.Retrieve.Filter).
     */
    List<SimilarWritingPrompt> nearest(float[] query, String topic, String cefrLevel, int k);

    /** Đề Writing active giống {@code query} ≥ minSimilarity, mọi topic/level, gần nhất trước, tối đa {@code limit}. */
    List<SimilarWritingPrompt> similarAtLeast(float[] query, double minSimilarity, int limit, UUID excludeQuestionId);

    /** Nội dung đề Writing (mọi trạng thái); rỗng nếu không có hoặc không phải writing_prompt. */
    Optional<String> promptText(UUID questionId);

    /** Ghi embedding cho đề Writing; false nếu không có dòng nào khớp. */
    boolean storeEmbedding(UUID questionId, float[] vector, String model);

    /** Đề cần tạo bù, bỏ qua {@code skip} (đã thử trong lần chạy này). */
    List<BackfillCandidate> backfillCandidates(String currentModel, int limit, Collection<UUID> skip);

    /** Ghi embedding và chuyển đề chưa từng kích hoạt sang active; false nếu đề không còn ở trạng thái đó. */
    boolean storeEmbeddingAndActivate(UUID questionId, float[] vector, String model);

    /** @param pendingActivation true = đề chưa từng kích hoạt (retired, retired_at NULL, chưa có embedding) */
    record BackfillCandidate(UUID questionId, String questionText, boolean pendingActivation) {
    }
}
