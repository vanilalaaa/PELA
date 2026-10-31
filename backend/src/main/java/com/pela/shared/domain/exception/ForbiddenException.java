package com.pela.shared.domain.exception;

/** Đã xác thực nhưng không có quyền (HTTP 403). */
public class ForbiddenException extends PelaException {

    public ForbiddenException(String message) {
        this("FORBIDDEN", message, null);
    }

    public ForbiddenException(String code, String message, Object details) {
        super(code, message, details);
    }
}
