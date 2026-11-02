package com.pela.rag.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pela.rag.application.port.in.ServedPrompt;
import com.pela.rag.application.port.out.PromptCachePort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * rag_question_cache (HNSW partial index WHERE status = 'active') và rag_cache_serve. Khoảng cách {@code <=>} là cosine
 * distance, similarity = 1 - distance. Truy vấn gần nhất chạy trong transaction để {@code SET LOCAL} chỉ có hiệu lực cho
 * câu đó.
 */
@Component
public class JdbcPromptCacheAdapter implements PromptCachePort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcPromptCacheAdapter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<ServedPrompt> findHit(CacheLookup lookup) {
        String vector = JdbcWritingPromptVectorAdapter.literal(lookup.queryEmbedding());
        String typeFilter = lookup.taskType().isPresent() ? "AND c.question_type = ?" : "";
        List<Object> args = new java.util.ArrayList<>(List.of(vector, Timestamp.from(lookup.now()), lookup.topic(),
                lookup.cefrLevel()));
        lookup.taskType().ifPresent(args::add);
        args.add(lookup.userId());
        args.add(Timestamp.from(lookup.notServedSince()));
        args.add(lookup.minSimilarity());
        List<ServedPrompt> rows = tx.execute(status -> {
            // RAG.Retrieve.Filter: lọc topic/level/đã phát làm hụt kết quả thì HNSW quét tiếp
            jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
            return jdbc.query("""
                    WITH candidates AS MATERIALIZED (
                        SELECT c.cache_id, c.question_type, c.generated_question::text AS generated_question,
                               c.query_embedding <=> ?::vector AS distance
                        FROM rag_question_cache c
                        WHERE c.status = 'active' AND c.expires_at > ? AND c.topic = ? AND c.cefr_level = ? %s
                          AND NOT EXISTS (SELECT 1 FROM rag_cache_serve s
                                          WHERE s.cache_id = c.cache_id AND s.user_id = ? AND s.served_at >= ?)
                        ORDER BY distance
                        LIMIT 5
                    )
                    SELECT * FROM candidates WHERE 1 - distance >= ? ORDER BY distance, cache_id LIMIT 1
                    """.formatted(typeFilter), (rs, i) -> served(rs.getObject("cache_id", UUID.class),
                            rs.getString("question_type"), rs.getString("generated_question")), args.toArray());
        });
        return rows == null ? Optional.empty() : rows.stream().findFirst();
    }

    @Override
    public void storeAndServe(NewCacheEntry entry, UUID userId, Instant now) {
        ObjectNode question = JSON.createObjectNode();
        question.put("prompt_text", entry.prompt().promptText().strip());
        question.put("task_type", entry.taskType());
        question.put("min_words", entry.prompt().minWords());
        question.put("max_words", entry.prompt().maxWords());
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO rag_question_cache (cache_id, query_embedding, topic, cefr_level, question_type,
                                                    generated_question, source_template_id, created_at, expires_at)
                    VALUES (?, ?::vector, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    """, entry.cacheId(), JdbcWritingPromptVectorAdapter.literal(entry.queryEmbedding()), entry.topic(),
                    entry.cefrLevel(), entry.taskType(), question.toString(), entry.sourceTemplateId(),
                    Timestamp.from(now), Timestamp.from(entry.expiresAt()));
            insertServe(entry.cacheId(), userId, now);
        });
    }

    @Override
    public void serveHit(UUID cacheId, UUID userId, Instant now) {
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE rag_question_cache SET hit_count = hit_count + 1, last_hit_at = ? WHERE cache_id = ?",
                    Timestamp.from(now), cacheId);
            insertServe(cacheId, userId, now);
        });
    }

    @Override
    public int countServedBetween(UUID userId, Instant from, Instant to) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM rag_cache_serve WHERE user_id = ? AND served_at >= ? AND served_at < ?",
                Integer.class, userId, Timestamp.from(from), Timestamp.from(to));
        return count == null ? 0 : count;
    }

    @Override
    public Optional<ServedPrompt> servedTo(UUID userId, UUID cacheId) {
        return jdbc.query("""
                SELECT c.cache_id, c.question_type, c.generated_question::text AS generated_question
                FROM rag_question_cache c
                WHERE c.cache_id = ? AND EXISTS (SELECT 1 FROM rag_cache_serve s WHERE s.cache_id = c.cache_id AND s.user_id = ?)
                """, (rs, i) -> served(rs.getObject("cache_id", UUID.class), rs.getString("question_type"),
                        rs.getString("generated_question")), cacheId, userId).stream().findFirst();
    }

    @Override
    public int expireBySourceTemplate(UUID questionId) {
        return jdbc.update("UPDATE rag_question_cache SET status = 'expired' WHERE source_template_id = ? AND status = 'active'",
                questionId);
    }

    @Override
    public int expireDue(Instant now) {
        return jdbc.update("UPDATE rag_question_cache SET status = 'expired' WHERE status = 'active' AND expires_at <= ?",
                Timestamp.from(now));
    }

    private void insertServe(UUID cacheId, UUID userId, Instant now) {
        jdbc.update("INSERT INTO rag_cache_serve (cache_id, user_id, served_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                cacheId, userId, Timestamp.from(now));
    }

    private static ServedPrompt served(UUID cacheId, String questionType, String generatedQuestion) {
        try {
            JsonNode question = JSON.readTree(generatedQuestion);
            return new ServedPrompt(cacheId, question.get("prompt_text").asText(), questionType,
                    question.get("min_words").asInt(), question.get("max_words").asInt());
        } catch (JsonProcessingException | NullPointerException e) {
            throw new IllegalStateException("rag_question_cache.generated_question is malformed: " + cacheId, e);
        }
    }
}
