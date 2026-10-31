package com.pela.shared.domain.exception;

/** Không tìm thấy tài nguyên (HTTP 404). */
public class NotFoundException extends PelaException {

    public NotFoundException(String message) {
        this("NOT_FOUND", message, null);
    }

    public NotFoundException(String code, String message, Object details) {
        super(code, message, details);
    }
}
