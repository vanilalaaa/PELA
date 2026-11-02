package com.pela.identity.adapter.in.job;

import com.pela.identity.application.port.in.ReleaseExpiredSuspensionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** UC-16 A2: mỗi giờ mở khoá các tài khoản có suspended_until đã qua. */
@Component
public class SuspensionReleaseJob {

    private static final Logger log = LoggerFactory.getLogger(SuspensionReleaseJob.class);

    private final ReleaseExpiredSuspensionUseCase release;

    public SuspensionReleaseJob(ReleaseExpiredSuspensionUseCase release) {
        this.release = release;
    }

    @Scheduled(cron = "${pela.jobs.suspension-release-cron:0 0 * * * *}")
    public void run() {
        int released = release.releaseAllExpired();
        if (released > 0) {
            log.info("Released {} expired suspensions", released);
        }
    }
}
