package com.pela.rag.adapter.in.job;

import com.pela.rag.application.port.in.BackfillPromptEmbeddingsUseCase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Tạo bù embedding đề Writing trên virtual thread: tự chạy khi khởi động profile dev (seed dev có đề chưa embed), và
 * khi Admin gọi POST /v1/admin/question-bank/embeddings/backfill. Mỗi lúc chỉ một lượt chạy.
 */
@Component
public class EmbedMissingPromptsJob {

    private static final Logger log = LoggerFactory.getLogger(EmbedMissingPromptsJob.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final BackfillPromptEmbeddingsUseCase backfill;
    private final Environment environment;
    private final boolean jobsEnabled;
    private final AtomicBoolean running = new AtomicBoolean();

    public EmbedMissingPromptsJob(BackfillPromptEmbeddingsUseCase backfill, Environment environment,
            @Value("${pela.jobs.enabled:true}") boolean jobsEnabled) {
        this.backfill = backfill;
        this.environment = environment;
        this.jobsEnabled = jobsEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (jobsEnabled && environment.acceptsProfiles(Profiles.of("dev"))) {
            start();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /** @return false nếu đang có lượt chạy khác */
    public boolean start() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        EXECUTOR.submit(() -> {
            try {
                backfill.backfill();
            } catch (RuntimeException e) {
                log.error("Prompt embedding backfill failed: {}", e.getClass().getSimpleName());
            } finally {
                running.set(false);
            }
        });
        return true;
    }
}
