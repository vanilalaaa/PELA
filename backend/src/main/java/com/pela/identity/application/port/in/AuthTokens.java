package com.pela.identity.application.port.in;

/** Token trả cho client sau đăng nhập / làm mới phiên (Auth.Login.Session). */
public record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds, String tokenType) {

    @Override
    public String toString() {
        return "AuthTokens[expiresIn=" + expiresInSeconds + "]";
    }
}
