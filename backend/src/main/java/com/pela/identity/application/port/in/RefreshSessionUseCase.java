package com.pela.identity.application.port.in;

/** Làm mới phiên khi app đang dùng (Auth.Login.Session). */
public interface RefreshSessionUseCase {

    AuthTokens refresh(String refreshToken);
}
