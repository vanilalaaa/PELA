package com.pela.shared.application.port.out;

/**
 * Embedding văn bản cho RAG / phát hiện đề trùng. Trả vector đã chuẩn hoá L2, số chiều theo
 * {@code ai.embedding.dimensions} (768). Lỗi ném {@link AiCallException}.
 */
public interface EmbeddingPort {

    float[] embed(String text, TaskType taskType);

    enum TaskType {
        /** Văn bản được lưu để tìm kiếm (đề Writing trong question_bank). */
        RETRIEVAL_DOCUMENT,
        /** Câu truy vấn lúc tìm kiếm. */
        RETRIEVAL_QUERY
    }
}
