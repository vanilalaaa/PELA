package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.rag.adapter.out.persistence.JdbcPromptCacheAdapter;
import com.pela.rag.adapter.out.persistence.JdbcWritingPromptVectorAdapter;
import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.rag.application.port.out.PromptCachePort.CacheLookup;
import com.pela.support.PgvectorContainer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * COM-3: tìm kiếm pgvector (RAG.Retrieve) và tra cache (RAG.Cache) với **10.000 đề Writing** và **10.000 entry cache**, trên
 * Postgres local (Testcontainers pgvector). Không gọi Gemini, không chạm Supabase. Kiểm tra:
 * <ul>
 *   <li>Truy hồi RAG.Retrieve (lọc topic + level, khoảng 400 đề mỗi cặp): P95 trong 1/4 ngân sách PER-3 (&le; 1000 ms). Với bộ lọc
 *       chọn lọc như vậy Postgres chọn index B-tree rồi sắp xếp, không dùng HNSW (kết quả chính xác, recall 1.0); kế hoạch được
 *       in ra;</li>
 *   <li>Tìm KHÔNG lọc trên cả 10.000 đề (phát hiện đề trùng, {@code similarAtLeast}): ở quy mô này Postgres vẫn chọn quét tuần
 *       tự (chính xác) nên kế hoạch thật cũng được in ra. Để đo chất lượng của chính index HNSW, câu tìm được ÉP dùng index
 *       (tắt quét tuần tự, bitmap và Sort): kiểm tra planner dùng index HNSW và Recall@5 so với tìm chính xác &ge; 0.90 (COM-3);</li>
 *   <li>P95 nhánh cache hit (tra cache + ghi lần phát) &le; 300 ms (PER-9), mọi truy vấn đều trúng.</li>
 * </ul>
 * Dữ liệu là vector TỔNG HỢP (cụm quanh tâm của từng cặp topic + level, nhiễu Gauss), không phải embedding thật, nên số đo là
 * chỉ báo về quy mô, không thay cho đo trên dữ liệu thật. Chậm (vài phút) nên chỉ chạy khi đặt {@code RAG_SCALE_TEST=true}:
 * <pre>RAG_SCALE_TEST=true ./gradlew test --tests "com.pela.rag.evaluation.RagIndexScaleTest" -i</pre>
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RAG_SCALE_TEST", matches = "true")
class RagIndexScaleTest {

    private static final int DIMS = 768;
    private static final int PROMPTS = 10_000;
    private static final int CACHE_ENTRIES = 10_000;
    private static final int K = 5;
    private static final int RECALL_QUERIES = 100;
    private static final int LATENCY_QUERIES = 200;
    private static final int CACHE_PROBES = 200;
    private static final double SIGMA = 0.03; // cos(đề, tâm cụm) khoảng 0.75, gần với độ tương đồng đề cùng chủ đề thật
    private static final double RECALL_TARGET = 0.90;
    private static final double RETRIEVAL_P95_BUDGET_MS = 1000;
    private static final double CACHE_HIT_P95_BUDGET_MS = 300;

    @Test
    void retrievalAndCacheLookupScaleToTenThousandEntries() throws Exception {
        Random random = new Random(42);
        List<String[]> pairs = new ArrayList<>(RagEvalSupport.readQueries().stream()
                .map(q -> new String[] {q.topic(), q.level()}).map(p -> p[0] + "|" + p[1]).distinct()
                .map(p -> p.split("\\|")).toList());
        assertThat(pairs).hasSize(24);
        List<float[]> centroids = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            centroids.add(normalized(gaussian(random, 1.0)));
        }

        String url = PgvectorContainer.jdbcUrlFor("rag_scale");
        String user = PgvectorContainer.instance().getUsername();
        String password = PgvectorContainer.instance().getPassword();
        Flyway.configure().dataSource(new DriverManagerDataSource(url, user, password))
                .locations("classpath:db/stub", "filesystem:../db/migrations").load().migrate();
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, user, password, true);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            UUID admin = UUID.randomUUID();
            jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"Scale\"}')",
                    admin, "scale-" + admin + "@example.test");
            jdbc.update("UPDATE users SET role = 'admin', status = 'active' WHERE user_id = ?", admin);

            long loadStart = System.nanoTime();
            // Nạp hàng loạt rồi mới dựng index (cùng định nghĩa như V1): nhanh hơn nhiều so với chèn từng dòng vào index
            jdbc.execute("DROP INDEX idx_qb_embedding_hnsw");
            jdbc.execute("DROP INDEX idx_rag_cache_embedding_hnsw");
            insertPrompts(jdbc, random, pairs, centroids, admin);
            List<float[]> cacheVectors = insertCache(jdbc, random, pairs, centroids);
            jdbc.execute("""
                    CREATE INDEX idx_qb_embedding_hnsw ON question_bank
                        USING hnsw (embedding_vector vector_cosine_ops)
                        WHERE status = 'active' AND question_purpose = 'writing_prompt'
                    """);
            jdbc.execute("""
                    CREATE INDEX idx_rag_cache_embedding_hnsw ON rag_question_cache
                        USING hnsw (query_embedding vector_cosine_ops) WHERE status = 'active'
                    """);
            jdbc.execute("ANALYZE question_bank");
            jdbc.execute("ANALYZE rag_question_cache");
            double loadSeconds = (System.nanoTime() - loadStart) / 1e9;

            JdbcWritingPromptVectorAdapter vectors = new JdbcWritingPromptVectorAdapter(jdbc, tx);
            JdbcPromptCacheAdapter cache = new JdbcPromptCacheAdapter(jdbc, tx);

            // --- Truy hồi có lọc topic + level (RAG.Retrieve): so với tìm chính xác, in kế hoạch thực thi
            List<Double> filteredRecalls = new ArrayList<>();
            for (int i = 0; i < RECALL_QUERIES; i++) {
                int p = random.nextInt(pairs.size());
                float[] query = normalized(add(centroids.get(p), gaussian(random, SIGMA)));
                Set<UUID> approx = new HashSet<>();
                vectors.nearest(query, pairs.get(p)[0], pairs.get(p)[1], K).forEach(s -> approx.add(s.questionId()));
                Set<UUID> exact = new HashSet<>(exactTopK(jdbc, tx, query, pairs.get(p)[0], pairs.get(p)[1]));
                filteredRecalls.add((double) approx.stream().filter(exact::contains).count() / K);
            }
            double filteredRecall = filteredRecalls.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            String filteredPlan = explain(jdbc, tx, normalized(add(centroids.get(0), gaussian(random, SIGMA))), pairs.get(0));

            // --- Tìm không lọc trên toàn bộ 10.000 đề: đo chất lượng của chính index HNSW (ép dùng index), COM-3
            List<Double> hnswRecalls = new ArrayList<>();
            List<Double> actualRecalls = new ArrayList<>();
            List<Double> unfilteredLatency = new ArrayList<>();
            for (int i = 0; i < RECALL_QUERIES; i++) {
                int p = random.nextInt(pairs.size());
                float[] query = normalized(add(centroids.get(p), gaussian(random, SIGMA)));
                long start = System.nanoTime();
                List<SimilarWritingPrompt> top = vectors.similarAtLeast(query, -1.0, K, null);
                unfilteredLatency.add((System.nanoTime() - start) / 1e6);
                Set<UUID> exact = new HashSet<>(exactTopKUnfiltered(jdbc, tx, query));
                Set<UUID> actual = new HashSet<>();
                top.forEach(s -> actual.add(s.questionId()));
                actualRecalls.add((double) actual.stream().filter(exact::contains).count() / K);
                Set<UUID> hnsw = new HashSet<>(hnswTopKUnfiltered(jdbc, tx, query));
                hnswRecalls.add((double) hnsw.stream().filter(exact::contains).count() / K);
            }
            double meanRecall = hnswRecalls.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double actualRecall = actualRecalls.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            float[] planQuery = normalized(add(centroids.get(0), gaussian(random, SIGMA)));
            String plan = explainUnfiltered(jdbc, tx, planQuery, true);
            String actualPlan = explainUnfiltered(jdbc, tx, planQuery, false);

            // --- Độ trễ bước truy hồi
            List<Double> retrieval = new ArrayList<>();
            for (int i = 0; i < LATENCY_QUERIES; i++) {
                int p = random.nextInt(pairs.size());
                float[] query = normalized(add(centroids.get(p), gaussian(random, SIGMA)));
                long start = System.nanoTime();
                List<SimilarWritingPrompt> top = vectors.nearest(query, pairs.get(p)[0], pairs.get(p)[1], K);
                retrieval.add((System.nanoTime() - start) / 1e6);
                assertThat(top).hasSize(K);
            }

            // --- Nhánh cache hit: mỗi lần thăm dò một entry khác nhau (learner chưa nhận entry nào)
            List<Double> cacheHit = new ArrayList<>();
            int misses = 0;
            for (int i = 0; i < CACHE_PROBES; i++) {
                int idx = i * (CACHE_ENTRIES / CACHE_PROBES);
                String[] pair = pairs.get(idx % pairs.size());
                Instant now = Instant.now();
                long start = System.nanoTime();
                var hit = cache.findHit(new CacheLookup(cacheVectors.get(idx), pair[0], pair[1], Optional.empty(), 0.92, admin,
                        now.minus(Duration.ofDays(14)), now));
                hit.ifPresent(h -> cache.serveHit(h.cacheId(), admin, now));
                cacheHit.add((System.nanoTime() - start) / 1e6);
                if (hit.isEmpty()) {
                    misses++;
                }
            }

            double retrievalP95 = EvalStats.percentile(retrieval, 95);
            double cacheP95 = EvalStats.percentile(cacheHit, 95);
            List<String> rows = List.of("metric,value",
                    "prompts," + PROMPTS, "cache_entries," + CACHE_ENTRIES, "load_seconds," + fmt(loadSeconds),
                    "recall_at_5_hnsw_forced," + String.format(Locale.ROOT, "%.3f", meanRecall),
                    "recall_at_5_unfiltered_actual_plan," + String.format(Locale.ROOT, "%.3f", actualRecall),
                    "recall_at_5_filtered_retrieval," + String.format(Locale.ROOT, "%.3f", filteredRecall),
                    "unfiltered_p95_ms," + fmt(EvalStats.percentile(unfilteredLatency, 95)),
                    "retrieval_p50_ms," + fmt(EvalStats.percentile(retrieval, 50)), "retrieval_p95_ms," + fmt(retrievalP95),
                    "cache_hit_p50_ms," + fmt(EvalStats.percentile(cacheHit, 50)), "cache_hit_p95_ms," + fmt(cacheP95),
                    "cache_misses," + misses);
            var out = RagEvalSupport.write("com3-scale.csv", rows);
            System.out.println("COM-3 scale (" + PROMPTS + " prompts, " + CACHE_ENTRIES + " cache entries, synthetic clustered"
                    + " vectors) -> " + out);
            rows.forEach(row -> System.out.println("  " + row.replace(',', '=')));
            System.out.println("  filtered retrieval uses HNSW index: " + filteredPlan.contains("idx_qb_embedding_hnsw")
                    + " (plan: " + filteredPlan.lines().findFirst().orElse("") + ")");
            System.out.println("  unfiltered search, actual plan uses HNSW index: " + actualPlan.contains("idx_qb_embedding_hnsw")
                    + " (plan: " + actualPlan.lines().findFirst().orElse("") + ")");
            System.out.println("  unfiltered search, forced index plan uses HNSW index: " + plan.contains("idx_qb_embedding_hnsw"));

            assertThat(plan).as("query plan of the unfiltered search with the index forced").contains("idx_qb_embedding_hnsw");
            assertThat(meanRecall).as("recall@5 of HNSW vs exact search (unfiltered)").isGreaterThanOrEqualTo(RECALL_TARGET);
            assertThat(retrievalP95).as("P95 retrieval latency (ms)").isLessThanOrEqualTo(RETRIEVAL_P95_BUDGET_MS);
            assertThat(misses).as("every cache probe must hit its own entry").isZero();
            assertThat(cacheP95).as("P95 cache-hit latency (ms)").isLessThanOrEqualTo(CACHE_HIT_P95_BUDGET_MS);
        } finally {
            dataSource.destroy();
        }
    }

    private static void insertPrompts(JdbcTemplate jdbc, Random random, List<String[]> pairs, List<float[]> centroids, UUID admin) {
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < PROMPTS; i++) {
            int p = i % pairs.size();
            batch.add(new Object[] {UUID.randomUUID(), "Synthetic prompt " + i, pairs.get(p)[0], pairs.get(p)[1],
                    RagEvalSupport.vectorLiteral(normalized(add(centroids.get(p), gaussian(random, SIGMA)))), admin});
            if (batch.size() == 500 || i == PROMPTS - 1) {
                jdbc.batchUpdate("""
                        INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, payload, topic,
                                                   cefr_level, status, embedding_vector, embedding_model, created_by)
                        VALUES (?, ?, 'essay', 'writing_prompt', '{"min_words":100,"max_words":200}'::jsonb, ?, ?, 'active',
                                ?::vector, 'synthetic', ?)
                        """, batch);
                batch.clear();
            }
        }
    }

    /** @return vector của từng entry cache, theo thứ tự chèn */
    private static List<float[]> insertCache(JdbcTemplate jdbc, Random random, List<String[]> pairs, List<float[]> centroids) {
        List<float[]> vectors = new ArrayList<>();
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < CACHE_ENTRIES; i++) {
            int p = i % pairs.size();
            float[] vector = normalized(add(centroids.get(p), gaussian(random, SIGMA)));
            vectors.add(vector);
            batch.add(new Object[] {UUID.randomUUID(), RagEvalSupport.vectorLiteral(vector), pairs.get(p)[0], pairs.get(p)[1],
                    Timestamp.from(Instant.now().plus(Duration.ofDays(30)))});
            if (batch.size() == 500 || i == CACHE_ENTRIES - 1) {
                jdbc.batchUpdate("""
                        INSERT INTO rag_question_cache (cache_id, query_embedding, topic, cefr_level, question_type,
                                                        generated_question, expires_at)
                        VALUES (?, ?::vector, ?, ?, 'essay',
                                '{"prompt_text":"Synthetic cached prompt","min_words":100,"max_words":200}'::jsonb, ?)
                        """, batch);
                batch.clear();
            }
        }
        return vectors;
    }

    /** Top-K chính xác: tắt index (quét tuần tự có sắp xếp) trong transaction. */
    private static List<UUID> exactTopK(JdbcTemplate jdbc, TransactionTemplate tx, float[] query, String topic, String level) {
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL enable_indexscan = off");
            jdbc.execute("SET LOCAL enable_bitmapscan = off");
            return jdbc.queryForList("""
                    SELECT question_id FROM question_bank
                    WHERE status = 'active' AND question_purpose = 'writing_prompt' AND topic = ? AND cefr_level = ?
                    ORDER BY embedding_vector <=> ?::vector LIMIT ?
                    """, UUID.class, topic, level, RagEvalSupport.vectorLiteral(query), K);
        });
    }

    /** Top-K chính xác trên toàn bộ đề active (không lọc topic/level). */
    private static List<UUID> exactTopKUnfiltered(JdbcTemplate jdbc, TransactionTemplate tx, float[] query) {
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL enable_indexscan = off");
            jdbc.execute("SET LOCAL enable_bitmapscan = off");
            return jdbc.queryForList("""
                    SELECT question_id FROM question_bank
                    WHERE status = 'active' AND question_purpose = 'writing_prompt'
                    ORDER BY embedding_vector <=> ?::vector LIMIT ?
                    """, UUID.class, RagEvalSupport.vectorLiteral(query), K);
        });
    }

    /** Chỉ index HNSW trả về kết quả đã sắp theo khoảng cách, nên tắt quét tuần tự, bitmap và Sort buộc planner dùng nó. */
    private static void forceHnsw(JdbcTemplate jdbc) {
        jdbc.execute("SET LOCAL enable_seqscan = off");
        jdbc.execute("SET LOCAL enable_bitmapscan = off");
        jdbc.execute("SET LOCAL enable_sort = off");
    }

    /** Top-K trên toàn bộ đề active, ÉP dùng index HNSW (tắt quét tuần tự): đo chất lượng của chính index. */
    private static List<UUID> hnswTopKUnfiltered(JdbcTemplate jdbc, TransactionTemplate tx, float[] query) {
        return tx.execute(status -> {
            forceHnsw(jdbc);
            jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
            return jdbc.queryForList("""
                    SELECT question_id FROM question_bank
                    WHERE status = 'active' AND question_purpose = 'writing_prompt'
                    ORDER BY embedding_vector <=> ?::vector LIMIT ?
                    """, UUID.class, RagEvalSupport.vectorLiteral(query), K);
        });
    }

    /** Kế hoạch thực thi của câu tìm không lọc (dạng câu của similarAtLeast); {@code forceIndex} tắt quét tuần tự. */
    private static String explainUnfiltered(JdbcTemplate jdbc, TransactionTemplate tx, float[] query, boolean forceIndex) {
        return tx.execute(status -> {
            if (forceIndex) {
                forceHnsw(jdbc);
            }
            jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
            return String.join("\n", jdbc.queryForList("""
                    EXPLAIN SELECT question_id FROM question_bank
                    WHERE status = 'active' AND question_purpose = 'writing_prompt'
                    ORDER BY embedding_vector <=> ?::vector LIMIT ?
                    """, String.class, RagEvalSupport.vectorLiteral(query), K));
        });
    }

    /** Kế hoạch thực thi của câu truy hồi RAG.Retrieve (cùng dạng câu với JdbcWritingPromptVectorAdapter.nearest). */
    private static String explain(JdbcTemplate jdbc, TransactionTemplate tx, float[] query, String[] pair) {
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
            return String.join("\n", jdbc.queryForList("""
                    EXPLAIN SELECT question_id FROM question_bank
                    WHERE status = 'active' AND question_purpose = 'writing_prompt' AND topic = ? AND cefr_level = ?
                    ORDER BY embedding_vector <=> ?::vector LIMIT ?
                    """, String.class, pair[0], pair[1], RagEvalSupport.vectorLiteral(query), K));
        });
    }

    private static float[] gaussian(Random random, double sigma) {
        float[] v = new float[DIMS];
        for (int i = 0; i < DIMS; i++) {
            v[i] = (float) (random.nextGaussian() * sigma);
        }
        return v;
    }

    private static float[] add(float[] a, float[] b) {
        float[] sum = new float[DIMS];
        for (int i = 0; i < DIMS; i++) {
            sum[i] = a[i] + b[i];
        }
        return sum;
    }

    private static float[] normalized(float[] v) {
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
