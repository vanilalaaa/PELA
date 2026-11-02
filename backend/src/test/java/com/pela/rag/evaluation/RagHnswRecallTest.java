package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * COM-3: recall@5 của HNSW so với tìm chính xác trên cùng truy vấn. Top-5 xấp xỉ = truy vấn thật của RAG.Retrieve
 * (index + iterative scan); Top-5 chính xác = cùng điều kiện, {@code SET LOCAL enable_indexscan = off}. Đo hai kiểu:
 * lọc topic + level (như BR-8) và không lọc (mọi đề active).
 */
@Tag("external")
class RagHnswRecallTest {

    private static final int K = 5;

    @Test
    void hnswRecallAt5IsAtLeast90Percent() throws Exception {
        try (RagEvalSupport support = RagEvalSupport.connect()) {
            EmbeddingPort embedding = support.embedding();
            // Truy vấn dựng bằng RagQueryBuilder: đúng hàm UC-24 dùng (RAG.Embed.Input)
            List<String[]> queries = new ArrayList<>();
            for (RagEvalSupport.QueryRow row : RagEvalSupport.readQueries()) {
                queries.add(new String[] {row.topic(), row.level(), row.text()});
            }
            int activePrompts = support.readOnly(() -> support.jdbc.queryForObject(
                    "SELECT count(*) FROM question_bank WHERE status = 'active' AND question_purpose = 'writing_prompt'",
                    Integer.class));

            List<Double> filtered = new ArrayList<>();
            List<Double> unfiltered = new ArrayList<>();
            boolean indexUsed = false;
            List<String> rows = new ArrayList<>();
            rows.add("query,mode,recall_at_5,approx_ids,exact_ids");
            for (String[] q : queries) {
                float[] v = embedding.embed(q[2], TaskType.RETRIEVAL_QUERY);
                List<UUID> approxF = ids(support.readOnly(() -> support.vectors.nearest(v, q[0], q[1], K)));
                List<UUID> exactF = exact(support, v, q[0], q[1]);
                List<UUID> approxU = ids(support.readOnly(() -> support.vectors.similarAtLeast(v, -1.0, K, null)));
                List<UUID> exactU = exact(support, v, null, null);
                filtered.add(recall(approxF, exactF));
                unfiltered.add(recall(approxU, exactU));
                rows.add(String.join(",", RagEvalSupport.csvCell(q[2]), "topic_level",
                        String.format(Locale.ROOT, "%.3f", recall(approxF, exactF)), join(approxF), join(exactF)));
                rows.add(String.join(",", RagEvalSupport.csvCell(q[2]), "all_active",
                        String.format(Locale.ROOT, "%.3f", recall(approxU, exactU)), join(approxU), join(exactU)));
                indexUsed |= usesIndex(support, v);
            }

            double meanFiltered = filtered.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double meanUnfiltered = unfiltered.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            var out = RagEvalSupport.write("hnsw-recall.csv", rows);
            System.out.println("COM-3 HNSW recall@5 -> " + out);
            System.out.printf(Locale.ROOT, "  active Writing prompts = %d, queries = %d, HNSW index used = %s%n",
                    activePrompts, queries.size(), indexUsed);
            System.out.printf(Locale.ROOT, "  recall@5 topic+level = %.3f, all active = %.3f (target >= 0.90)%n",
                    meanFiltered, meanUnfiltered);
            if (!indexUsed) {
                System.out.println("  WARNING: planner did not use idx_qb_embedding_hnsw (too few rows?). Recall is"
                        + " then trivially exact; re-run on the full prompt set (COM-3 targets 10,000 prompts)");
            }
            assertThat(meanFiltered).as("recall@5 topic+level").isGreaterThanOrEqualTo(0.90);
            assertThat(meanUnfiltered).as("recall@5 all active").isGreaterThanOrEqualTo(0.90);
        }
    }

    /** Top-5 chính xác bằng quét tuần tự; topic null = không lọc. */
    private static List<UUID> exact(RagEvalSupport support, float[] v, String topic, String level) {
        String vector = literal(v);
        return support.readOnly(() -> {
            support.jdbc.execute("SET LOCAL enable_indexscan = off");
            support.jdbc.execute("SET LOCAL enable_bitmapscan = off");
            if (topic == null) {
                return support.jdbc.queryForList("""
                        SELECT question_id FROM question_bank
                        WHERE status = 'active' AND question_purpose = 'writing_prompt'
                        ORDER BY embedding_vector <=> ?::vector, question_id LIMIT ?
                        """, UUID.class, vector, K);
            }
            return support.jdbc.queryForList("""
                    SELECT question_id FROM question_bank
                    WHERE status = 'active' AND question_purpose = 'writing_prompt' AND topic = ? AND cefr_level = ?
                    ORDER BY embedding_vector <=> ?::vector, question_id LIMIT ?
                    """, UUID.class, topic, level, vector, K);
        });
    }

    private static boolean usesIndex(RagEvalSupport support, float[] v) {
        String vector = literal(v);
        List<String> plan = support.readOnly(() -> support.jdbc.queryForList("""
                EXPLAIN SELECT question_id FROM question_bank
                WHERE status = 'active' AND question_purpose = 'writing_prompt'
                ORDER BY embedding_vector <=> ?::vector LIMIT 5
                """, String.class, vector));
        return plan.stream().anyMatch(line -> line.contains("idx_qb_embedding_hnsw"));
    }

    static double recall(List<UUID> approx, List<UUID> exact) {
        if (exact.isEmpty()) {
            return 1.0;
        }
        Set<UUID> hit = new HashSet<>(approx);
        hit.retainAll(exact);
        return (double) hit.size() / exact.size();
    }

    private static List<UUID> ids(List<SimilarWritingPrompt> prompts) {
        return prompts.stream().map(SimilarWritingPrompt::questionId).toList();
    }

    private static String join(List<UUID> ids) {
        return String.join(" ", ids.stream().map(UUID::toString).toList());
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ",").append(Float.toString(v[i]));
        }
        return sb.append(']').toString();
    }
}
