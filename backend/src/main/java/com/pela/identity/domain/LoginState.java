package com.pela.identity.domain;

import java.time.Instant;

/**
 * Bộ đếm đăng nhập sai của một tài khoản (Auth.Login.Lockout).
 *
 * @param failedCount     số lần sai trong cửa sổ hiện tại
 * @param windowStartedAt lần sai đầu tiên của cửa sổ; null khi chưa sai
 * @param lockedUntil     khoá tới thời điểm này; null khi không khoá
 */
public record LoginState(int failedCount, Instant windowStartedAt, Instant lockedUntil) {

    public static final LoginState CLEAN = new LoginState(0, null, null);

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }
}
