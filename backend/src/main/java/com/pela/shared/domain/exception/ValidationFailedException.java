package com.pela.shared.domain.exception;

/** Dữ liệu hợp lệ về cú pháp nhưng vi phạm luật nghiệp vụ (HTTP 422). */
public class ValidationFailedException extends PelaException {

    public ValidationFailedException(String message) {
        this("VALIDATION_FAILED", message, null);
    }

    public ValidationFailedException(String code, String message, Object details) {
        super(code, message, details);
    }
}
