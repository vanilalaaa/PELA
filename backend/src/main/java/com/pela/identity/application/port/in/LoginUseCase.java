package com.pela.identity.application.port.in;

/** UC-01 2.0: đăng nhập email/mật khẩu qua backend (Auth.Login, SEC-5). */
public interface LoginUseCase {

    AuthTokens login(String email, String password);
}
