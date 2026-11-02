package com.pela.rag.adapter.out.persistence;

import com.pela.rag.application.port.out.PromptExampleDetailsPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcPromptExampleDetailsAdapter implements PromptExampleDetailsPort {

    private final JdbcTemplate jdbc;

    public JdbcPromptExampleDetailsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, ExampleDetails> details(Collection<UUID> questionIds) {
        Map<UUID, ExampleDetails> result = new HashMap<>();
        if (questionIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT question_id, question_type, payload->>'min_words' AS min_words, payload->>'max_words' AS max_words
                FROM question_bank
                WHERE question_purpose = 'writing_prompt' AND question_id = ANY (?::uuid[])
                """, rs -> {
            result.put(rs.getObject("question_id", UUID.class), new ExampleDetails(rs.getString("question_type"),
                    number(rs.getString("min_words")), number(rs.getString("max_words"))));
        }, (Object) questionIds.stream().map(UUID::toString).toArray(String[]::new));
        return result;
    }

    private static Integer number(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
