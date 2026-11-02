package com.pela.shared.application.port.out;

/** Lỗi khi gọi nhà cung cấp AI (LLM, embedding, TTS), đã phân loại để use case chọn fallback (BR-7). */
public class AiCallException extends RuntimeException {

    public enum Kind {
        /** Quá thời gian chờ cho phép. */
        TIMEOUT,
        /** Output không parse được hoặc không qua Bean Validation — không dùng output này. */
        INVALID_JSON,
        /** Bị bộ lọc an toàn chặn (prompt hoặc output). */
        SAFETY_BLOCKED,
        /** Vượt hạn mức gọi (HTTP 429). */
        RATE_LIMITED,
        /** Lỗi khác từ phía nhà cung cấp hoặc kết nối. */
        UPSTREAM_ERROR
    }

    private final Kind kind;

    public AiCallException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiCallException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
