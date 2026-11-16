package com.pela.admin.adapter.in.job;

import com.pela.admin.application.port.in.ListeningImportJobUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nhận job nhập Listening còn queued (vd server khởi động lại trước khi kịp xử lý ngay). */
@Component
public class ImportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportJobRunner.class);
    private static final int BATCH = 5;

    private final ListeningImportJobUseCase imports;

    public ImportJobRunner(ListeningImportJobUseCase imports) {
        this.imports = imports;
    }

    @Scheduled(fixedDelayString = "${pela.jobs.import-queue-delay:PT1M}")
    public void run() {
        int processed = imports.processQueued(BATCH);
        if (processed > 0) {
            log.info("Processed {} queued listening import job(s)", processed);
        }
    }
}
