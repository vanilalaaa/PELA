package com.pela.writing.adapter.out.persistence;

import com.pela.writing.application.port.out.WritingPromptPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Đề Writing đang dùng trong question_bank (question_purpose = writing_prompt). */
@Component
public class JdbcWritingPromptAdapter implements WritingPromptPort {

    private final JdbcTemplate jdbc;

    public JdbcWritingPromptAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BankPrompt> activePrompt(UUID questionId) {
        return jdbc.query("""
                        SELECT question_id, question_text,
                               (payload->>'min_words')::int AS min_words,
                               (payload->>'max_words')::int AS max_words,
                               payload->>'rubric_hint' AS rubric_hint
                        FROM question_bank
                        WHERE question_id = ? AND question_purpose = 'writing_prompt' AND status = 'active'
                        """,
                (rs, i) -> new BankPrompt(rs.getObject("question_id", UUID.class), rs.getString("question_text"),
                        (Integer) rs.getObject("min_words"), (Integer) rs.getObject("max_words"),
                        rs.getString("rubric_hint")),
                questionId).stream().findFirst();
    }
}
