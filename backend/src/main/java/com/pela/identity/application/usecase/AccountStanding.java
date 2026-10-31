package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.ReleaseExpiredSuspensionUseCase;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.application.port.out.SystemSettingPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Quy tắc chung "tài khoản còn được dùng không" cho login, refresh và mọi request đã xác thực:
 * suspended còn hạn → ACCOUNT_SUSPENDED; suspended hết hạn → mở khoá (use case chung với job);
 * pending_deletion → ACCOUNT_PENDING_DELETION kèm ngày xoá (BR-33).
 */
@Component
class AccountStanding {

    static final String DELETION_GRACE_SETTING = "account.deletion_grace_days";

    private final SystemSettingPort settings;
    private final ReleaseExpiredSuspensionUseCase release;

    AccountStanding(SystemSettingPort settings, ReleaseExpiredSuspensionUseCase release) {
        this.settings = settings;
        this.release = release;
    }

    void requireUsable(UserAccount account, Instant now) {
        if (account.isSuspendedAt(now)) {
            throw AuthErrors.accountSuspended(account);
        }
        if (account.suspensionExpiredAt(now)) {
            release.releaseIfExpired(account.userId());
        }
        if (account.isPendingDeletion()) {
            throw AuthErrors.accountPendingDeletion(account.deletionScheduledAt(settings.requiredInt(DELETION_GRACE_SETTING)));
        }
    }
}
