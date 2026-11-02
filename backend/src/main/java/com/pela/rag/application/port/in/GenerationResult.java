package com.pela.rag.application.port.in;

import com.pela.rag.domain.FallbackReason;

/**
 * Kết quả UC-24: có đề AI (mới tạo hoặc lấy từ cache), hoặc cần chuyển sang đề có sẵn trong kho (BR-7). rag không biết
 * gì về kho đề của learner; module writing nhận {@link NeedsFallback} rồi tự chọn đề.
 */
public sealed interface GenerationResult {

    enum Source {
        GENERATED,
        CACHE_HIT
    }

    record Generated(ServedPrompt prompt, Source source) implements GenerationResult {
    }

    record NeedsFallback(FallbackReason reason) implements GenerationResult {
    }
}
