package com.pela.rag.application.usecase;

import com.pela.rag.application.port.in.ExpireRagCacheUseCase;
import com.pela.rag.application.port.in.GetServedPromptUseCase;
import com.pela.rag.application.port.in.ServedPrompt;
import com.pela.rag.application.port.out.PromptCachePort;
import com.pela.shared.application.port.out.ClockPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Đọc lại đề đã phát và hết hạn cache (BR-25). */
@Service
public class RagCacheService implements GetServedPromptUseCase, ExpireRagCacheUseCase {

    private final PromptCachePort cache;
    private final ClockPort clock;

    public RagCacheService(PromptCachePort cache, ClockPort clock) {
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    public Optional<ServedPrompt> servedTo(UUID userId, UUID cacheId) {
        return cache.servedTo(userId, cacheId);
    }

    @Override
    public int expireBySourceTemplate(UUID questionId) {
        return cache.expireBySourceTemplate(questionId);
    }

    @Override
    public int expireDue() {
        return cache.expireDue(clock.now());
    }
}
