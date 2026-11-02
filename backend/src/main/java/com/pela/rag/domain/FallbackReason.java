package com.pela.rag.domain;

/**
 * Lý do UC-24 không phát đề do AI tạo mà chuyển sang đề có sẵn trong kho (BR-7, RAG.Fallback.OnFailure).
 * Tên khớp {@code rag_generation_log.reason}.
 */
public enum FallbackReason {
    /** Top-1 similarity thấp hơn ngưỡng (hoặc không có đề mẫu nào) — BR-8. */
    BELOW_THRESHOLD,
    /** Quá ngân sách thời gian hoặc LLM/embedding quá thời gian chờ. */
    TIMEOUT,
    /** Output của LLM không phải JSON hợp lệ theo schema. */
    INVALID_JSON,
    /** Bị bộ lọc an toàn của nhà cung cấp AI chặn. */
    SAFETY_BLOCKED,
    /** Bị bộ lọc từ cấm của hệ thống (BR-15). */
    FILTERED,
    /** Đã dùng hết số lần "Để AI tạo đề" trong ngày (RAG.Limit.Daily). */
    DAILY_LIMIT,
    /** Không tạo được embedding cho truy vấn. */
    NO_EMBEDDING,
    /** JSON hợp lệ nhưng sai dạng bài, sai khoảng từ hoặc quá giống đề kho. */
    INVALID_OUTPUT,
    /** Nhà cung cấp AI lỗi hoặc giới hạn tốc độ. */
    UPSTREAM_ERROR
}
