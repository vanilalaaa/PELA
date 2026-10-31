package com.pela.shared.domain.exception;

/** Tài nguyên đang bị khoá, ví dụ tài khoản hay Final Test (HTTP 423). */
public class LockedException extends PelaException {

    public LockedException(String message) {
        this("LOCKED", message, null);
    }

    public LockedException(String code, String message, Object details) {
        super(code, message, details);
    }
}
