package com.pela.rag.adapter.out.persistence;

import com.pela.rag.application.port.out.GenerationLogPort;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcGenerationLogAdapter implements GenerationLogPort {

    private final JdbcTemplate jdbc;

    public JdbcGenerationLogAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Entry entry) {
        jdbc.update("""
                INSERT INTO rag_generation_log (user_id, topic, cefr_level, outcome, reason, top1_similarity, latency_ms,
                                                created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, entry.userId(), entry.topic(), entry.cefrLevel(), entry.outcome(),
                entry.reason() == null ? null : entry.reason().name(), entry.top1Similarity(), entry.latencyMs(),
                Timestamp.from(entry.createdAt()));
    }
}
