package com.pela.writing.adapter.in.job;

import com.pela.writing.application.port.in.GradeWritingUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Writing.Grade.Timeout: chấm các bài pending chưa được nhận (vd server khởi động lại) và chấm lại bài retry_queued
 * đã tới next_retry_at.
 */
@Component
public class WritingGradingJob {

    private final GradeWritingUseCase grading;
    private final int batchSize;

    public WritingGradingJob(GradeWritingUseCase grading, @Value("${pela.jobs.writing-grading-batch:10}") int batchSize) {
        this.grading = grading;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${pela.jobs.writing-grading-delay:PT30S}")
    public void run() {
        grading.gradeDue(batchSize);
    }
}
