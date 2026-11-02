package com.pela.rag.adapter.in.web;

import com.pela.rag.adapter.in.job.EmbedMissingPromptsJob;
import com.pela.shared.domain.exception.ConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin chạy tạo bù embedding đề Writing (quyền Admin kiểm ở AdminAccessInterceptor cho mọi /v1/admin/**). */
@RestController
public class AdminPromptEmbeddingController {

    private final EmbedMissingPromptsJob job;

    public AdminPromptEmbeddingController(EmbedMissingPromptsJob job) {
        this.job = job;
    }

    @PostMapping("/v1/admin/question-bank/embeddings/backfill")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> backfill() {
        if (!job.start()) {
            throw new ConflictException("BACKFILL_RUNNING", "Embedding backfill is already running", null);
        }
        return Map.of("status", "started");
    }
}
