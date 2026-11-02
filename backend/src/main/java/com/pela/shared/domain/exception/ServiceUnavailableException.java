package com.pela.shared.domain.exception;

/** Dịch vụ phụ thuộc (Supabase, Gemini…) tạm thời không dùng được (HTTP 503). */
public class ServiceUnavailableException extends PelaException {

    public ServiceUnavailableException(String message) {
        this("SERVICE_UNAVAILABLE", message, null);
    }

    public ServiceUnavailableException(String code, String message, Object details) {
        super(code, message, details);
    }
}
