package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.rag.application.port.in.GenerateWritingPromptUseCase.GenerateCommand;
import com.pela.rag.application.port.in.GenerationResult;
import com.pela.rag.application.port.in.GenerationResult.Generated;
import com.pela.rag.application.port.in.GenerationResult.NeedsFallback;
import com.pela.rag.application.port.out.PromptCachePort.CacheLookup;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PER-3 / PER-9: độ trễ P95 của pipeline sinh đề Writing (UC-24) trên Supabase dev và Gemini thật. Mỗi test chạy trong MỘT
 * transaction ghi rồi rollback ({@link RagEvalSupport.Pipeline#inRolledBackTransaction}): không để lại cache, lần phát
 * hay nhật ký. Số mẫu: biến {@code RAG_LATENCY_SAMPLES} (mặc định 20), lấy rải đều các dòng on_topic của queries.csv.
 * Tốn quota Gemini; không chạy trong ./gradlew build.
 */
@Tag("external")
class RagLatencyTest {

    private static final double GENERATE_P95_BUDGET_MS = 4000;
    private static final double CACHE_HIT_P95_BUDGET_MS = 300;
    private static final double CACHE_MIN_SIMILARITY = 0.92;
    private static final int DEFAULT_SAMPLES = 20;

    /** PER-3: end-to-end gồm cả chọn đề dự phòng BR-7 khi rag báo cần fallback. P95 ≤ 4 s. */
    @Test
    void generateP95IsWithin4Seconds() throws Exception {
        try (RagEvalSupport support = RagEvalSupport.connect()) {
            RagEvalSupport.Pipeline pipeline = support.pipeline();
            List<RagEvalSupport.QueryRow> sample = sample(RagEvalSupport.readQueries());
            long budgetMillis = pipeline.settings().requiredInt("ai.question_gen_p95_budget_ms");

            List<Double> millis = new ArrayList<>();
            Map<String, Integer> outcomes = new LinkedHashMap<>();
            List<String> rows = new ArrayList<>();
            rows.add("topic,level,interests,branch,millis");
            pipeline.inRolledBackTransaction(() -> {
                for (RagEvalSupport.QueryRow row : sample) {
                    pipeline.interests().set(row.interests());
                    long start = System.nanoTime();
                    GenerationResult result = pipeline.generate().generate(pipeline.userId(), new GenerateCommand(
                            row.topic(), row.level(), null, Instant.now().plusMillis(budgetMillis)));
                    if (result instanceof NeedsFallback) {
                        pipeline.curated().pick(pipeline.userId(), row.topic(), row.level());
                    }
                    double elapsed = (System.nanoTime() - start) / 1_000_000.0;
                    String branch = result instanceof Generated generated ? generated.source().name().toLowerCase(Locale.ROOT)
                            : "fallback:" + ((NeedsFallback) result).reason();
                    millis.add(elapsed);
                    outcomes.merge(branch, 1, Integer::sum);
                    rows.add(String.join(",", RagEvalSupport.csvCell(row.topic()), row.level(),
                            RagEvalSupport.csvCell(String.join("|", row.interests())), branch,
                            String.format(Locale.ROOT, "%.0f", elapsed)));
                }
                return null;
            });

            double p95 = EvalStats.percentile(millis, 95);
            var out = RagEvalSupport.write("latency-generate.csv", rows);
            System.out.println("PER-3 generate latency -> " + out);
            System.out.printf(Locale.ROOT, "  n=%d, p50=%.0f ms, p95=%.0f ms (target <= %.0f ms), outcomes=%s%n", millis.size(),
                    EvalStats.percentile(millis, 50), p95, GENERATE_P95_BUDGET_MS, outcomes);
            assertThat(p95).as("P95 generate latency (ms)").isLessThanOrEqualTo(GENERATE_P95_BUDGET_MS);
        }
    }

    /**
     * PER-9: nhánh cache hit (tra cache + ghi lần phát), KHÔNG tính bước embed truy vấn. Dựng trước entry cache cho mỗi
     * truy vấn mẫu, rồi đo findHit + serveHit. P95 ≤ 300 ms.
     */
    @Test
    void cacheHitP95IsWithin300MillisExcludingEmbedding() throws Exception {
        try (RagEvalSupport support = RagEvalSupport.connect()) {
            RagEvalSupport.Pipeline pipeline = support.pipeline();
            List<RagEvalSupport.QueryRow> sample = sample(RagEvalSupport.readQueries());

            List<Double> millis = new ArrayList<>();
            List<String> rows = new ArrayList<>();
            rows.add("topic,level,interests,millis");
            int misses = pipeline.inRolledBackTransaction(() -> {
                List<float[]> vectors = new ArrayList<>();
                for (RagEvalSupport.QueryRow row : sample) {
                    float[] vector = pipeline.embedding().embed(row.text(), TaskType.RETRIEVAL_QUERY);
                    vectors.add(vector);
                    support.jdbc.update("""
                            INSERT INTO rag_question_cache (cache_id, query_embedding, topic, cefr_level, question_type,
                                                            generated_question, expires_at)
                            VALUES (?, ?::vector, ?, ?, 'paragraph',
                                    '{"prompt_text":"Latency probe","min_words":30,"max_words":60}'::jsonb,
                                    now() + interval '30 days')
                            """, UUID.randomUUID(), RagEvalSupport.vectorLiteral(vector), row.topic(), row.level());
                }
                int missed = 0;
                for (int i = 0; i < sample.size(); i++) {
                    RagEvalSupport.QueryRow row = sample.get(i);
                    Instant now = Instant.now();
                    long start = System.nanoTime();
                    var hit = pipeline.cache().findHit(new CacheLookup(vectors.get(i), row.topic(), row.level(),
                            Optional.empty(), CACHE_MIN_SIMILARITY, pipeline.userId(), now.minus(Duration.ofDays(14)), now));
                    hit.ifPresent(prompt -> pipeline.cache().serveHit(prompt.cacheId(), pipeline.userId(), now));
                    double elapsed = (System.nanoTime() - start) / 1_000_000.0;
                    if (hit.isEmpty()) {
                        missed++;
                    }
                    millis.add(elapsed);
                    rows.add(String.join(",", RagEvalSupport.csvCell(row.topic()), row.level(),
                            RagEvalSupport.csvCell(String.join("|", row.interests())),
                            String.format(Locale.ROOT, "%.0f", elapsed)));
                }
                return missed;
            });

            double p95 = EvalStats.percentile(millis, 95);
            var out = RagEvalSupport.write("latency-cache-hit.csv", rows);
            System.out.println("PER-9 cache-hit latency (excluding embedding) -> " + out);
            System.out.printf(Locale.ROOT, "  n=%d, p50=%.0f ms, p95=%.0f ms (target <= %.0f ms), misses=%d%n", millis.size(),
                    EvalStats.percentile(millis, 50), p95, CACHE_HIT_P95_BUDGET_MS, misses);
            assertThat(misses).as("every probe query must hit its own cache entry").isZero();
            assertThat(p95).as("P95 cache-hit latency (ms)").isLessThanOrEqualTo(CACHE_HIT_P95_BUDGET_MS);
        }
    }

    /** Lấy tối đa N dòng on_topic (không kho_gap) rải đều trong file, để phủ nhiều cặp topic + level. */
    private static List<RagEvalSupport.QueryRow> sample(List<RagEvalSupport.QueryRow> all) throws Exception {
        List<RagEvalSupport.QueryRow> onTopic = all.stream().filter(q -> q.onTopic() && !q.khoGap()).toList();
        int wanted = Math.min(onTopic.size(), Integer.parseInt(Optional.ofNullable(RagEvalSupport.config("RAG_LATENCY_SAMPLES"))
                .orElse(String.valueOf(DEFAULT_SAMPLES))));
        List<RagEvalSupport.QueryRow> picked = new ArrayList<>();
        for (int i = 0; i < wanted; i++) {
            picked.add(onTopic.get((int) ((long) i * onTopic.size() / wanted)));
        }
        return picked;
    }
}
