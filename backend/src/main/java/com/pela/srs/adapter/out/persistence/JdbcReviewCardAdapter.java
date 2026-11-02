package com.pela.srs.adapter.out.persistence;

import com.pela.srs.application.port.out.ReviewCardPort;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcReviewCardAdapter implements ReviewCardPort {

    private final JdbcTemplate jdbc;

    public JdbcReviewCardAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> wordsInPool(UUID userId, Collection<String> lowercaseWords) {
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(lowercaseWords);
        String placeholders = String.join(",", java.util.Collections.nCopies(lowercaseWords.size(), "?"));
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT lower(v.word) FROM review_card rc
                JOIN vocabulary v ON v.vocab_id = rc.vocab_id
                WHERE rc.user_id = ? AND rc.in_pool AND lower(v.word) IN (%s)
                """.formatted(placeholders), String.class, args.toArray()));
    }

    @Override
    public UUID upsertForWritingReview(UUID userId, UUID vocabId, LocalDate nextReviewDate) {
        // Thẻ mới: origin = writing, vào pool ngay. Thẻ đã có: đưa vào pool và kéo ngày ôn về hôm nay nếu đang xa hơn.
        return jdbc.queryForObject("""
                INSERT INTO review_card (user_id, vocab_id, next_review_date, status, origin, in_pool)
                VALUES (?, ?, ?, 'new', 'writing', true)
                ON CONFLICT (user_id, vocab_id) DO UPDATE
                SET in_pool = true,
                    next_review_date = LEAST(review_card.next_review_date, EXCLUDED.next_review_date)
                RETURNING card_id
                """, UUID.class, userId, vocabId, Date.valueOf(nextReviewDate));
    }
}
