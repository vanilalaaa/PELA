package com.pela.rag.adapter.in.job;

import com.pela.rag.application.port.in.ExpireRagCacheUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** BR-25: mỗi ngày chuyển entry rag_question_cache quá 30 ngày sang expired (giữ lại để audit, không còn là cache hit). */
@Component
public class RagCacheExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RagCacheExpiryJob.class);

    private final ExpireRagCacheUseCase cache;

    public RagCacheExpiryJob(ExpireRagCacheUseCase cache) {
        this.cache = cache;
    }

    @Scheduled(cron = "${pela.jobs.rag-cache-expiry-cron:0 30 3 * * *}")
    public void run() {
        int expired = cache.expireDue();
        if (expired > 0) {
            log.info("Expired {} RAG cache entries past their TTL", expired);
        }
    }
}
