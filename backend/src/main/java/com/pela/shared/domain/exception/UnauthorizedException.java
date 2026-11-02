package com.pela.shared.domain.exception;

/** Xác thực thất bại, ví dụ sai mật khẩu hay refresh token hết hạn (HTTP 401). */
public class UnauthorizedException extends PelaException {

    public UnauthorizedException(String message) {
        this("UNAUTHENTICATED", message, null);
    }

    public UnauthorizedException(String code, String message, Object details) {
        super(code, message, details);
    }
}
