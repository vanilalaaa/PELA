package com.pela.srs.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Bảng review_card. */
public interface ReviewCardPort {

    Set<String> wordsInPool(UUID userId, Collection<String> lowercaseWords);

    UUID upsertForWritingReview(UUID userId, UUID vocabId, LocalDate nextReviewDate);
}
