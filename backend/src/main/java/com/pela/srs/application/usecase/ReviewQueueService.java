package com.pela.srs.application.usecase;

import com.pela.srs.application.port.in.ReviewQueueUseCase;
import com.pela.srs.application.port.out.ReviewCardPort;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReviewQueueService implements ReviewQueueUseCase {

    private final ReviewCardPort cards;

    public ReviewQueueService(ReviewCardPort cards) {
        this.cards = cards;
    }

    @Override
    public Set<String> wordsInReviewPool(UUID userId, Collection<String> words) {
        Set<String> lowercase = words.stream()
                .filter(w -> w != null && !w.isBlank())
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return lowercase.isEmpty() ? Set.of() : cards.wordsInPool(userId, lowercase);
    }

    @Override
    public UUID pushForReviewToday(UUID userId, UUID vocabId, LocalDate today) {
        return cards.upsertForWritingReview(userId, vocabId, today);
    }
}
