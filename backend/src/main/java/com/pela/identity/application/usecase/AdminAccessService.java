package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.AdminAccessUseCase;
import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.domain.exception.ForbiddenException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * SEC-3: role + status đọc từ bảng users (không tin claim JWT). Kết quả cache {@link #CACHE_TTL} nên
 * khoá/bỏ quyền Admin có hiệu lực chậm nhất sau khoảng thời gian đó.
 */
@Service
public class AdminAccessService implements AdminAccessUseCase {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final UserAccountPort accounts;
    private final ClockPort clock;
    private final Map<UUID, Decision> cache = new ConcurrentHashMap<>();

    public AdminAccessService(UserAccountPort accounts, ClockPort clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public void requireActiveAdmin(UUID userId) {
        Instant now = clock.now();
        Decision decision = cache.get(userId);
        if (decision == null || !now.isBefore(decision.expiresAt())) {
            boolean allowed = accounts.findById(userId).map(UserAccount::isActiveAdmin).orElse(false);
            decision = new Decision(allowed, now.plus(CACHE_TTL));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(userId, decision);
        }
        if (!decision.allowed()) {
            throw new ForbiddenException("ADMIN_REQUIRED", "Admin access is required", null);
        }
    }

    private record Decision(boolean allowed, Instant expiresAt) {
    }
}
