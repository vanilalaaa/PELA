package com.pela.identity.application.port.in;

/** Auth.Logout: thoát phiên hiện tại hoặc mọi thiết bị. */
public interface LogoutUseCase {

    void logout(String accessToken, boolean allDevices);
}
