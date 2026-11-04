package com.pela.writing.adapter.out.persistence;

import com.pela.writing.application.port.out.CuratedPromptPort;
import com.pela.writing.domain.PromptCandidate;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcCuratedPromptAdapter implements CuratedPromptPort {

    private static final int MAX_CANDIDATES = 500;

    private final JdbcTemplate jdbc;

    public JdbcCuratedPromptAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PromptCandidate> candidates(UUID userId, String topic, String cefrLevel) {
        // "Đã làm" = đã nộp bài (không phải nháp) với đề này
        return jdbc.query("""
                SELECT q.question_id, q.question_text, q.question_type,
                       q.payload->>'min_words' AS min_words, q.payload->>'max_words' AS max_words,
                       (SELECT max(w.submitted_at) FROM writing_submission w
                        WHERE w.user_id = ? AND w.prompt_question_id = q.question_id AND w.grading_status <> 'draft')
                           AS last_done_at
                FROM question_bank q
                WHERE q.question_purpose = 'writing_prompt' AND q.status = 'active' AND q.topic = ? AND q.cefr_level = ?
                ORDER BY q.created_at, q.question_id
                LIMIT ?
                """, (rs, i) -> {
            Timestamp lastDone = rs.getTimestamp("last_done_at");
            return new PromptCandidate(rs.getObject("question_id", UUID.class), rs.getString("question_text"),
                    rs.getString("question_type"), number(rs.getString("min_words")), number(rs.getString("max_words")),
                    lastDone == null ? null : lastDone.toInstant());
        }, userId, topic, cefrLevel, MAX_CANDIDATES);
    }

    private static Integer number(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
