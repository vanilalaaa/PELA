package com.pela.shared.domain.exception;

/**
 * Gốc của mọi exception nghiệp vụ. Không biết gì về HTTP: adapter web quyết định mã trạng thái
 * theo loại exception (xem {@code GlobalExceptionHandler}).
 */
public abstract class PelaException extends RuntimeException {

    private final String code;
    private final transient Object details;

    protected PelaException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    /** Mã lỗi ổn định dạng UPPER_SNAKE_CASE, client dựa vào mã này (không dựa vào message). */
    public String code() {
        return code;
    }

    /** Thông tin bổ sung an toàn để trả cho client; không chứa PII. Có thể null. */
    public Object details() {
        return details;
    }
}
