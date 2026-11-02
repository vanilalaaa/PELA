package com.pela.rag.adapter.out.persistence;

import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.rag.application.port.out.WritingPromptVectorPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * pgvector trên question_bank. Khoảng cách {@code <=>} là cosine distance, similarity = 1 - distance. Tìm kiếm chạy
 * trong transaction để {@code SET LOCAL hnsw.iterative_scan} chỉ có hiệu lực cho câu truy vấn đó (tham gia transaction
 * của người gọi nếu đang có).
 */
@Component
public class JdbcWritingPromptVectorAdapter implements WritingPromptVectorPort {

    private static final String ACTIVE_WRITING = "status = 'active' AND question_purpose = 'writing_prompt'";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcWritingPromptVectorAdapter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public List<SimilarWritingPrompt> nearest(float[] query, String topic, String cefrLevel, int k) {
        String vector = literal(query);
        return withIterativeScan(() -> jdbc.query("""
                        WITH candidates AS MATERIALIZED (
                            SELECT question_id, question_text, topic, cefr_level, embedding_vector <=> ?::vector AS distance
                            FROM question_bank
                            WHERE %s AND topic = ? AND cefr_level = ?
                            ORDER BY distance
                            LIMIT ?
                        )
                        SELECT * FROM candidates ORDER BY distance, question_id
                        """.formatted(ACTIVE_WRITING),
                JdbcWritingPromptVectorAdapter::similar, vector, topic, cefrLevel, k));
    }

    @Override
    public List<SimilarWritingPrompt> similarAtLeast(float[] query, double minSimilarity, int limit,
            UUID excludeQuestionId) {
        String vector = literal(query);
        // Lấy Top-N theo index rồi mới lọc ngưỡng (điều kiện ngưỡng trong WHERE sẽ làm mất index HNSW)
        return withIterativeScan(() -> jdbc.query("""
                        WITH candidates AS MATERIALIZED (
                            SELECT question_id, question_text, topic, cefr_level, embedding_vector <=> ?::vector AS distance
                            FROM question_bank
                            WHERE %s AND question_id IS DISTINCT FROM ?
                            ORDER BY distance
                            LIMIT ?
                        )
                        SELECT * FROM candidates WHERE 1 - distance >= ? ORDER BY distance, question_id
                        """.formatted(ACTIVE_WRITING),
                JdbcWritingPromptVectorAdapter::similar, vector, excludeQuestionId, limit, minSimilarity));
    }

    @Override
    public Optional<String> promptText(UUID questionId) {
        return jdbc.queryForList(
                "SELECT question_text FROM question_bank WHERE question_id = ? AND question_purpose = 'writing_prompt'",
                String.class, questionId).stream().findFirst();
    }

    @Override
    public boolean storeEmbedding(UUID questionId, float[] vector, String model) {
        return jdbc.update("""
                UPDATE question_bank SET embedding_vector = ?::vector, embedding_model = ?
                WHERE question_id = ? AND question_purpose = 'writing_prompt'
                """, literal(vector), model, questionId) > 0;
    }

    @Override
    public List<BackfillCandidate> backfillCandidates(String currentModel, int limit, Collection<UUID> skip) {
        return jdbc.query("""
                        SELECT question_id, question_text, status <> 'active' AS pending_activation
                        FROM question_bank
                        WHERE question_purpose = 'writing_prompt'
                          AND ((status = 'active' AND embedding_model IS DISTINCT FROM ?)
                            OR (status = 'retired' AND retired_at IS NULL AND embedding_vector IS NULL))
                          AND NOT (question_id = ANY (?::uuid[]))
                        ORDER BY created_at, question_id
                        LIMIT ?
                        """,
                (rs, i) -> new BackfillCandidate(rs.getObject("question_id", UUID.class), rs.getString("question_text"),
                        rs.getBoolean("pending_activation")),
                currentModel, skip.stream().map(UUID::toString).toArray(String[]::new), limit);
    }

    @Override
    public boolean storeEmbeddingAndActivate(UUID questionId, float[] vector, String model) {
        return jdbc.update("""
                UPDATE question_bank SET embedding_vector = ?::vector, embedding_model = ?, status = 'active'
                WHERE question_id = ? AND question_purpose = 'writing_prompt'
                  AND status = 'retired' AND retired_at IS NULL AND embedding_vector IS NULL
                """, literal(vector), model, questionId) > 0;
    }

    private <T> T withIterativeScan(java.util.function.Supplier<T> query) {
        return tx.execute(status -> {
            // RAG.Retrieve.Filter: lọc topic/level làm hụt K thì HNSW quét tiếp; thứ tự được sắp lại ở CTE ngoài
            jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
            return query.get();
        });
    }

    private static SimilarWritingPrompt similar(ResultSet rs, int row) throws SQLException {
        return new SimilarWritingPrompt(rs.getObject("question_id", UUID.class), rs.getString("question_text"),
                rs.getString("topic"), rs.getString("cefr_level"), 1 - rs.getDouble("distance"));
    }

    /** Dạng văn bản của pgvector: [x1,x2,...]. */
    static String literal(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Float.toString(vector[i]));
        }
        return sb.append(']').toString();
    }
}
