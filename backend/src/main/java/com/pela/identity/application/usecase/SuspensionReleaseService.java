package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.ReleaseExpiredSuspensionUseCase;
import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.shared.application.port.out.ClockPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SuspensionReleaseService implements ReleaseExpiredSuspensionUseCase {

    private final UserAccountPort accounts;
    private final ClockPort clock;

    public SuspensionReleaseService(UserAccountPort accounts, ClockPort clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public boolean releaseIfExpired(UUID userId) {
        return accounts.releaseExpiredSuspension(userId, clock.now());
    }

    @Override
    public int releaseAllExpired() {
        return accounts.releaseAllExpiredSuspensions(clock.now());
    }
}
