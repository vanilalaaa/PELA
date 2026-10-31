package com.pela.identity.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Auth.Login.Lockout (SEC-5): sai {@code maxAttempts} lần liên tiếp trong {@code window} thì khoá
 * {@code lockDuration}. Đăng nhập đúng thì xoá bộ đếm. Ngưỡng đọc từ system_setting.
 */
public record LockoutPolicy(int maxAttempts, Duration window, Duration lockDuration) {

    public LockoutPolicy {
        if (maxAttempts < 1 || window.isNegative() || window.isZero() || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("Invalid lockout policy");
        }
    }

    public LoginState onFailure(LoginState current, Instant now) {
        if (current.isLockedAt(now)) {
            return current;
        }
        boolean windowOpen = current.windowStartedAt() != null
                && now.isBefore(current.windowStartedAt().plus(window));
        int failures = windowOpen ? current.failedCount() + 1 : 1;
        Instant windowStart = windowOpen ? current.windowStartedAt() : now;
        if (failures >= maxAttempts) {
            return new LoginState(0, null, now.plus(lockDuration));
        }
        return new LoginState(failures, windowStart, null);
    }

    public LoginState onSuccess() {
        return LoginState.CLEAN;
    }
}
