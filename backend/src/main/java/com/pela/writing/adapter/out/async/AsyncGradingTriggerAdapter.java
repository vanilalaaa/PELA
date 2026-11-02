package com.pela.writing.adapter.out.async;

import com.pela.writing.application.port.in.GradeWritingUseCase;
import com.pela.writing.application.port.out.GradingTriggerPort;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chấm ngay sau khi nộp trên virtual thread, để learner không phải chờ job theo lịch. Tắt bằng
 * {@code pela.writing.grade-immediately=false} (test); khi đó {@code WritingGradingJob} vẫn chấm.
 */
@Component
public class AsyncGradingTriggerAdapter implements GradingTriggerPort {

    private static final Logger log = LoggerFactory.getLogger(AsyncGradingTriggerAdapter.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ObjectProvider<GradeWritingUseCase> grading;
    private final boolean enabled;

    public AsyncGradingTriggerAdapter(ObjectProvider<GradeWritingUseCase> grading,
            @Value("${pela.writing.grade-immediately:true}") boolean enabled) {
        this.grading = grading;
        this.enabled = enabled;
    }

    @Override
    public void gradeSoon(UUID submissionId) {
        if (!enabled) {
            return;
        }
        EXECUTOR.submit(() -> {
            try {
                grading.getObject().gradeOne(submissionId);
            } catch (RuntimeException e) {
                // Job theo lịch sẽ nhận lại bài sau khi hết hạn giữ
                log.warn("Immediate grading failed: submission={}, {}", submissionId, e.getClass().getSimpleName());
            }
        });
    }
}
