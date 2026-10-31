package com.pela.identity.application.port.out;

/** Lỗi từ Supabase Auth đã được phân loại. Message chỉ mô tả loại lỗi, không chứa dữ liệu người dùng. */
public class SupabaseAuthException extends RuntimeException {

    public enum Reason {
        INVALID_CREDENTIALS,
        EMAIL_NOT_CONFIRMED,
        USER_ALREADY_EXISTS,
        WEAK_PASSWORD,
        INVALID_INPUT,
        SAME_PASSWORD,
        INVALID_OTP,
        INVALID_REFRESH_TOKEN,
        INVALID_ACCESS_TOKEN,
        USER_NOT_FOUND,
        RATE_LIMITED,
        UNAVAILABLE,
        UNKNOWN
    }

    private final Reason reason;

    public SupabaseAuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public SupabaseAuthException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
