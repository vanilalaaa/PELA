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

    /**
     * @param reason tên {@link FallbackReason} (BELOW_THRESHOLD, TIMEOUT, …) dạng chuỗi: module khác chỉ cần chuỗi này,
     *               không phụ thuộc domain của rag
     */
    record NeedsFallback(String reason) implements GenerationResult {

        public static NeedsFallback of(FallbackReason reason) {
            return new NeedsFallback(reason.name());
        }
    }
}
