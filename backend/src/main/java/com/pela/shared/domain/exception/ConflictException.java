package com.pela.shared.domain.exception;

/** Xung đột trạng thái, ví dụ trùng dữ liệu (HTTP 409). */
public class ConflictException extends PelaException {

    public ConflictException(String message) {
        this("CONFLICT", message, null);
    }

    public ConflictException(String code, String message, Object details) {
        super(code, message, details);
    }
}
