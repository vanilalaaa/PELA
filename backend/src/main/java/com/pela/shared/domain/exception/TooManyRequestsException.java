package com.pela.shared.domain.exception;

/** Vượt giới hạn số lần, ví dụ lượt chấm Writing/ngày (HTTP 429). */
public class TooManyRequestsException extends PelaException {

    public TooManyRequestsException(String message) {
        this("TOO_MANY_REQUESTS", message, null);
    }

    public TooManyRequestsException(String code, String message, Object details) {
        super(code, message, details);
    }
}
