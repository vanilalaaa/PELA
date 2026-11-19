package com.pela.admin.adapter.out.async;

import com.pela.admin.application.port.in.ListeningImportJobUseCase;
import com.pela.admin.application.port.out.ImportTriggerPort;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Xử lý file nhập ngay trên virtual thread. Tắt bằng {@code pela.imports.process-immediately=false} (test); khi đó
 * {@code ImportJobRunner} vẫn nhận job queued theo lịch.
 */
@Component
public class AsyncImportTriggerAdapter implements ImportTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(AsyncImportTriggerAdapter.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ObjectProvider<ListeningImportJobUseCase> imports;
    private final boolean enabled;

    public AsyncImportTriggerAdapter(ObjectProvider<ListeningImportJobUseCase> imports,
            @Value("${pela.imports.process-immediately:true}") boolean enabled) {
        this.imports = imports;
        this.enabled = enabled;
    }

    @Override
    public void processSoon(UUID jobId) {
        if (!enabled) {
            return;
        }
        EXECUTOR.submit(() -> {
            try {
                imports.getObject().process(jobId);
            } catch (RuntimeException e) {
                log.warn("Immediate import processing failed: job={}, {}", jobId, e.getClass().getSimpleName());
            }
        });
    }
}
