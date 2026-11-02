package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.AccountStatusGuardUseCase;
import com.pela.identity.application.port.in.AdminAccessUseCase;
import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.domain.exception.ForbiddenException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Kiểm tra theo bảng users cho mọi request đã xác thực (không tin claim JWT): trạng thái tài khoản và
 * quyền Admin (SEC-3). Bản ghi được cache {@link #CACHE_TTL}, nên khoá tài khoản / bỏ quyền Admin có
 * hiệu lực chậm nhất sau khoảng thời gian đó.
 */
@Service
public class AccountAccessService implements AccountStatusGuardUseCase, AdminAccessUseCase {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final UserAccountPort accounts;
    private final ClockPort clock;
    private final AccountStanding standing;
    private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();

    AccountAccessService(UserAccountPort accounts, ClockPort clock, AccountStanding standing) {
        this.accounts = accounts;
        this.clock = clock;
        this.standing = standing;
    }

    @Override
    public void requireUsableAccount(UUID userId) {
        Instant now = clock.now();
        Optional<UserAccount> account = load(userId, now);
        // Chưa có dòng users (vd vừa đăng ký): không chặn ở đây; endpoint cần hồ sơ tự trả 404
        if (account.isPresent()) {
            boolean expiredSuspension = account.get().suspensionExpiredAt(now);
            standing.requireUsable(account.get(), now);
            if (expiredSuspension) {
                cache.remove(userId);
            }
        }
    }

    @Override
    public void requireActiveAdmin(UUID userId) {
        boolean allowed = load(userId, clock.now()).map(UserAccount::isActiveAdmin).orElse(false);
        if (!allowed) {
            throw new ForbiddenException("ADMIN_REQUIRED", "Admin access is required", null);
        }
    }

    private Optional<UserAccount> load(UUID userId, Instant now) {
        Snapshot snapshot = cache.get(userId);
        if (snapshot == null || !now.isBefore(snapshot.expiresAt())) {
            snapshot = new Snapshot(accounts.findById(userId), now.plus(CACHE_TTL));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(userId, snapshot);
        }
        return snapshot.account();
    }

    private record Snapshot(Optional<UserAccount> account, Instant expiresAt) {
    }
}
