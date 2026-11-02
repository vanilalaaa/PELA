package com.pela.srs.application.port.in;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Hàng ôn SM-2 của learner, cho các module khác (Writing) đưa từ vào ôn. */
public interface ReviewQueueUseCase {

    /** Những từ (so khớp không phân biệt hoa thường) learner đã có thẻ trong review pool. Trả về dạng chữ thường. */
    Set<String> wordsInReviewPool(UUID userId, Collection<String> words);

    /**
     * BR-9: thêm thẻ cho từ (hoặc đưa thẻ có sẵn vào pool) với next_review_date = {@code today} (ngày theo múi giờ
     * learner), origin = writing. Idempotent; trả card_id.
     */
    UUID pushForReviewToday(UUID userId, UUID vocabId, LocalDate today);
}
