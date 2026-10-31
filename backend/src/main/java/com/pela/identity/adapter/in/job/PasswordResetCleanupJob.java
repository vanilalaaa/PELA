package com.pela.identity.adapter.in.job;

import com.pela.identity.application.port.in.PasswordRecoveryUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dọn phiên khôi phục quá hạn và thu hồi phiên Supabase tương ứng. */
@Component
public class PasswordResetCleanupJob {

    private final PasswordRecoveryUseCase recovery;

    public PasswordResetCleanupJob(PasswordRecoveryUseCase recovery) {
        this.recovery = recovery;
    }

    @Scheduled(fixedDelayString = "${pela.jobs.password-reset-cleanup-delay:PT1M}")
    public void run() {
        recovery.purgeExpired();
    }
}
