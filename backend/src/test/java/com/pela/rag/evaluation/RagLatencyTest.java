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
 * PER-3 / PER-9: độ trễ P95 của pipeline sinh đề Writing (UC-24), đo chi tiết từng bước (db, embed, llm, filter, cache_write)
 * và độ trễ một vòng đi về tới DB ({@code SELECT 1}). Gemini luôn được gọi thật (tốn quota).
 *
 * <p>Chế độ ({@code RAG_LATENCY_MODE}):
 * <ul>
 *   <li>{@code remote} (mặc định): DB là Supabase dev, đo từ máy dev. Độ trễ mạng tới DB chiếm phần lớn, nên chỉ để tham khảo
 *       và KHÔNG kiểm tra ngưỡng PER-3/PER-9.</li>
 *   <li>{@code local}: Postgres local (Testcontainers pgvector) nạp 150 đề + embedding từ fixture
 *       {@code docs/evaluation/rag/fixtures/question-bank-embeddings.csv}; kiểm tra P95 ≤ 4 s và ≤ 300 ms.</li>
 * </ul>
 * Mọi thứ chạy trong một transaction ghi rồi rollback. Biến: {@code RAG_LATENCY_SAMPLES} (mặc định 20) và
 * {@code RAG_LATENCY_PAUSE_MS} (nghỉ giữa các lượt sinh đề để tránh hạn mức Gemini, mặc định 0; không tính vào thời gian đo).
 */
@Tag("external")
class RagLatencyTest {

    private static final double GENERATE_P95_BUDGET_MS = 4000;
    private static final double CACHE_HIT_P95_BUDGET_MS = 300;
    private static final double CACHE_MIN_SIMILARITY = 0.92;
    private static final int DEFAULT_SAMPLES = 20;
    private static final int RTT_PROBES = 20;
    private static final List<String> STAGES = List.of(StageTimer.DB, StageTimer.EMBED, StageTimer.LLM,
            StageTimer.FILTER, StageTimer.CACHE_WRITE);

    /** PER-3: end-to-end gồm cả chọn đề dự phòng BR-7 khi rag báo cần fallback. P95 ≤ 4 s (chỉ kiểm ở chế độ local). */
    @Test
    void generateP95IsWithin4Seconds() throws Exception {
        boolean local = isLocal();
        try (RagEvalSupport support = local ? RagEvalSupport.connectLocal() : RagEvalSupport.connect()) {
            StageTimer timer = new StageTimer();
            RagEvalSupport.Pipeline pipeline = support.pipeline(timer);
            List<RagEvalSupport.QueryRow> sample = sample(RagEvalSupport.readQueries());
            long budgetMillis = pipeline.settings().requiredInt("ai.question_gen_p95_budget_ms");
            long pauseMillis = Long.parseLong(Optional.ofNullable(RagEvalSupport.config("RAG_LATENCY_PAUSE_MS")).orElse("0"));
            double rtt = roundTripMillis(support);

            List<Double> millis = new ArrayList<>();
            Map<String, List<Double>> byStage = new LinkedHashMap<>();
            STAGES.forEach(stage -> byStage.put(stage, new ArrayList<>()));
            Map<String, Integer> outcomes = new LinkedHashMap<>();
            List<String> rows = new ArrayList<>();
            rows.add("topic,level,interests,branch,total_ms,db_ms,embed_ms,llm_ms,filter_ms,cache_write_ms,other_ms");
            pipeline.inRolledBackTransaction(() -> {
                for (int i = 0; i < sample.size(); i++) {
                    RagEvalSupport.QueryRow row = sample.get(i);
                    pipeline.interests().set(row.interests());
                    timer.reset();
                    long start = System.nanoTime();
                    GenerationResult result = pipeline.generate().generate(pipeline.userId(), new GenerateCommand(
                            row.topic(), row.level(), null, Instant.now().plusMillis(budgetMillis)));
                    if (result instanceof NeedsFallback) {
                        pipeline.curated().pick(pipeline.userId(), row.topic(), row.level());
                    }
                    double total = (System.nanoTime() - start) / 1_000_000.0;
                    String branch = result instanceof Generated generated ? generated.source().name().toLowerCase(Locale.ROOT)
                            : "fallback:" + ((NeedsFallback) result).reason();
                    millis.add(total);
                    outcomes.merge(branch, 1, Integer::sum);
                    List<String> cells = new ArrayList<>(List.of(RagEvalSupport.csvCell(row.topic()), row.level(),
                            RagEvalSupport.csvCell(String.join("|", row.interests())), branch, ms(total)));
                    for (String stage : STAGES) {
                        byStage.get(stage).add(timer.millis(stage));
                        cells.add(ms(timer.millis(stage)));
                    }
                    cells.add(ms(total - timer.totalMillis()));
                    rows.add(String.join(",", cells));
                    if (pauseMillis > 0 && i < sample.size() - 1) {
                        sleep(pauseMillis);
                    }
                }
                return null;
            });

            double p95 = EvalStats.percentile(millis, 95);
            var out = RagEvalSupport.write("latency-generate.csv", rows);
            System.out.println("PER-3 generate latency (" + (local ? "local Postgres" : "REMOTE Supabase dev, network dominated")
                    + ") -> " + out);
            System.out.printf(Locale.ROOT, "  n=%d, p50=%.0f ms, p95=%.0f ms (target <= %.0f ms), outcomes=%s%n", millis.size(),
                    EvalStats.percentile(millis, 50), p95, GENERATE_P95_BUDGET_MS, outcomes);
            System.out.printf(Locale.ROOT, "  DB round trip (SELECT 1) p50 = %.1f ms%n", rtt);
            for (String stage : STAGES) {
                List<Double> values = byStage.get(stage);
                System.out.printf(Locale.ROOT, "  stage %-11s p50=%6.0f ms  p95=%6.0f ms%n", stage,
                        EvalStats.percentile(values, 50), EvalStats.percentile(values, 95));
            }
            if (local) {
                assertThat(p95).as("P95 generate latency (ms)").isLessThanOrEqualTo(GENERATE_P95_BUDGET_MS);
            } else {
                System.out.println("  remote mode: PER-3 not asserted (use RAG_LATENCY_MODE=local for a comparable figure)");
            }
        }
    }

    /**
     * PER-9: nhánh cache hit (tra cache + ghi lần phát), KHÔNG tính bước embed truy vấn. Dựng trước entry cache cho mỗi truy vấn
     * mẫu, rồi đo findHit + serveHit. P95 ≤ 300 ms (chỉ kiểm ở chế độ local).
     */
    @Test
    void cacheHitP95IsWithin300MillisExcludingEmbedding() throws Exception {
        boolean local = isLocal();
        try (RagEvalSupport support = local ? RagEvalSupport.connectLocal() : RagEvalSupport.connect()) {
            RagEvalSupport.Pipeline pipeline = support.pipeline();
            List<RagEvalSupport.QueryRow> sample = sample(RagEvalSupport.readQueries());
            double rtt = roundTripMillis(support);

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
                            RagEvalSupport.csvCell(String.join("|", row.interests())), ms(elapsed)));
                }
                return missed;
            });

            double p95 = EvalStats.percentile(millis, 95);
            var out = RagEvalSupport.write("latency-cache-hit.csv", rows);
            System.out.println("PER-9 cache-hit latency excluding embedding (" + (local ? "local Postgres"
                    : "REMOTE Supabase dev, network dominated") + ") -> " + out);
            System.out.printf(Locale.ROOT, "  n=%d, p50=%.0f ms, p95=%.0f ms (target <= %.0f ms), misses=%d, DB round trip p50 = %.1f ms%n",
                    millis.size(), EvalStats.percentile(millis, 50), p95, CACHE_HIT_P95_BUDGET_MS, misses, rtt);
            assertThat(misses).as("every probe query must hit its own cache entry").isZero();
            if (local) {
                assertThat(p95).as("P95 cache-hit latency (ms)").isLessThanOrEqualTo(CACHE_HIT_P95_BUDGET_MS);
            } else {
                System.out.println("  remote mode: PER-9 not asserted (use RAG_LATENCY_MODE=local for a comparable figure)");
            }
        }
    }

    private static boolean isLocal() throws Exception {
        return "local".equalsIgnoreCase(RagEvalSupport.config("RAG_LATENCY_MODE"));
    }

    /** p50 của {@value #RTT_PROBES} lần {@code SELECT 1}: độ trễ một vòng đi về tới DB. */
    private static double roundTripMillis(RagEvalSupport support) {
        List<Double> probes = new ArrayList<>();
        for (int i = 0; i < RTT_PROBES; i++) {
            long start = System.nanoTime();
            support.jdbc.queryForObject("SELECT 1", Integer.class);
            probes.add((System.nanoTime() - start) / 1_000_000.0);
        }
        return EvalStats.percentile(probes, 50);
    }

    private static String ms(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
