package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.rag.application.port.in.GenerateWritingPromptUseCase.GenerateCommand;
import com.pela.rag.application.port.in.GenerationResult;
import com.pela.rag.application.port.in.GenerationResult.Generated;
import com.pela.rag.application.port.in.GenerationResult.NeedsFallback;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PER-3 chất lượng: sinh 50 đề Writing bằng pipeline UC-24 thật (Supabase dev + Gemini), rải đều các cặp topic + level
 * và các sở thích trong queries.csv, ghi build/evaluation/rag/generated.csv theo đúng cột của
 * docs/evaluation/rag/generated.csv (id, topic, level, prompt_text + 4 cột trống cho 2 người chấm). Chạy trong MỘT
 * transaction rồi rollback: không để lại cache hay nhật ký. Chỉ đề do AI mới tạo ("generated") được đưa vào mẫu; lượt
 * fallback hay cache hit chỉ được đếm. Chấm xong thì chạy {@link RagRatingReportTest}. Tốn quota Gemini.
 *
 * <p>Bài này đo CHẤT LƯỢNG đề, KHÔNG đo thời gian (dùng {@link RagLatencyTest} cho độ trễ), nên hạn chót mỗi lượt là
 * {@code RAG_SAMPLE_DEADLINE_MS} (mặc định 60000 ms, không phải ngân sách 4 s của UC-24) và có thể nghỉ giữa các lượt bằng
 * {@code RAG_SAMPLE_PAUSE_MS} (mặc định 0) để không vượt hạn mức Gemini (RPM).
 */
@Tag("external")
class RagGenerationSampleTest {

    static final int SAMPLE_SIZE = 50;
    static final long DEFAULT_DEADLINE_MS = 60_000;
    static final String HEADER = "id,topic,level,prompt_text,on_topic_r1,right_level_r1,on_topic_r2,right_level_r2";

    @Test
    void generateFiftyPromptsForManualReview() throws Exception {
        try (RagEvalSupport support = RagEvalSupport.connect()) {
            RagEvalSupport.Pipeline pipeline = support.pipeline();
            List<RagEvalSupport.QueryRow> requests = interleavedByPair(RagEvalSupport.readQueries());
            long deadlineMillis = Long.parseLong(Optional.ofNullable(RagEvalSupport.config("RAG_SAMPLE_DEADLINE_MS"))
                    .orElse(String.valueOf(DEFAULT_DEADLINE_MS)));
            long pauseMillis = Long.parseLong(Optional.ofNullable(RagEvalSupport.config("RAG_SAMPLE_PAUSE_MS")).orElse("0"));

            List<String> rows = new ArrayList<>();
            rows.add(HEADER);
            Map<String, Integer> outcomes = new LinkedHashMap<>();
            pipeline.inRolledBackTransaction(() -> {
                int generated = 0;
                int made = 0;
                for (RagEvalSupport.QueryRow row : requests) {
                    if (generated >= SAMPLE_SIZE) {
                        break;
                    }
                    if (made++ > 0 && pauseMillis > 0) {
                        sleep(pauseMillis);
                    }
                    pipeline.interests().set(row.interests());
                    GenerationResult result = pipeline.generate().generate(pipeline.userId(), new GenerateCommand(
                            row.topic(), row.level(), null, Instant.now().plusMillis(deadlineMillis)));
                    if (result instanceof Generated g && g.source() == GenerationResult.Source.GENERATED) {
                        generated++;
                        outcomes.merge("generated", 1, Integer::sum);
                        rows.add(String.join(",", String.format("g%02d", generated), RagEvalSupport.csvCell(row.topic()),
                                row.level(), RagEvalSupport.csvCell(g.prompt().promptText()), "", "", "", ""));
                    } else {
                        outcomes.merge(result instanceof Generated ? "cache_hit"
                                : "fallback:" + ((NeedsFallback) result).reason(), 1, Integer::sum);
                    }
                }
                return null;
            });

            var out = RagEvalSupport.write("generated.csv", rows);
            System.out.println("PER-3 generated sample -> " + out);
            System.out.println("  outcomes = " + outcomes + " (two reviewers fill the 4 empty columns independently)");
            assertThat(rows.size() - 1).as("generated prompts for review (outcomes: %s)", outcomes).isEqualTo(SAMPLE_SIZE);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Xen kẽ các cặp topic + level để 50 đề đầu tiên phủ đều nhiều cặp. Bỏ dòng kho_gap. */
    private static List<RagEvalSupport.QueryRow> interleavedByPair(List<RagEvalSupport.QueryRow> all) {
        Map<String, List<RagEvalSupport.QueryRow>> byPair = new LinkedHashMap<>();
        for (RagEvalSupport.QueryRow row : all) {
            if (!row.khoGap()) {
                byPair.computeIfAbsent(row.topic() + "/" + row.level(), k -> new ArrayList<>()).add(row);
            }
        }
        List<RagEvalSupport.QueryRow> result = new ArrayList<>();
        int max = byPair.values().stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < max; i++) {
            for (List<RagEvalSupport.QueryRow> rows : byPair.values()) {
                if (i < rows.size()) {
                    result.add(rows.get(i));
                }
            }
        }
        return result;
    }
}
