package com.pela.roadmap.adapter.out.persistence;

import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase.CatalogEntry;
import com.pela.roadmap.application.port.out.VocabularyPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcVocabularyAdapter implements VocabularyPort {

    private final JdbcTemplate jdbc;

    public JdbcVocabularyAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, CatalogEntry> findCurated(Collection<String> lowercaseWords) {
        List<Object> args = new ArrayList<>(lowercaseWords);
        String placeholders = String.join(",", Collections.nCopies(lowercaseWords.size(), "?"));
        Map<String, CatalogEntry> result = new HashMap<>();
        // Cùng một từ có thể có nhiều từ loại; lấy bản đầu tiên theo vocab_id để kết quả ổn định
        jdbc.query("""
                SELECT DISTINCT ON (lower(word)) lower(word) AS word, vocab_id, cefr_level
                FROM vocabulary
                WHERE owner_user_id IS NULL AND lower(word) IN (%s)
                ORDER BY lower(word), vocab_id
                """.formatted(placeholders),
                rs -> {
                    result.put(rs.getString("word"),
                            new CatalogEntry(rs.getObject("vocab_id", UUID.class), rs.getString("cefr_level")));
                }, args.toArray());
        return result;
    }

    @Override
    public UUID insertPrivateWordIfAbsent(UUID ownerId, String word, String meaningVi, String exampleSentence,
            String cefrLevel) {
        jdbc.update("""
                INSERT INTO vocabulary (word, meaning, example_sentence, cefr_level, source, owner_user_id)
                VALUES (?, ?, ?, ?, 'writing', ?)
                ON CONFLICT DO NOTHING
                """, word, meaningVi, exampleSentence, cefrLevel, ownerId);
        return jdbc.queryForObject("""
                SELECT vocab_id FROM vocabulary
                WHERE owner_user_id = ? AND lower(word) = lower(?) AND part_of_speech IS NULL
                """, UUID.class, ownerId, word);
    }
}
