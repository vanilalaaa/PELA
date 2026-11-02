package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AS-7 / BR-8: đo similarity Top-1 (lọc topic + level, như RAG.Retrieve) cho từng truy vấn trong
 * docs/evaluation/rag/queries.csv, ghi build/evaluation/rag/threshold.csv, in phân bố hai nhóm và ngưỡng đề xuất.
 * KHÔNG sửa system_setting: chỉ đề xuất, nhóm quyết định rồi sửa tay.
 */
@Tag("external")
class RagThresholdCalibrationTest {

    private static final String KHO_GAP = "kho_gap";

    record Query(String topic, String level, String text, boolean onTopic, String note) {
    }

    @Test
    void calibrateRetrievalThreshold() throws Exception {
        try (RagEvalSupport support = RagEvalSupport.connect()) {
            EmbeddingPort embedding = support.embedding();
            List<Query> queries = RagEvalSupport.readCsv(RagEvalSupport.EVAL_DIR.resolve("queries.csv"),
                    c -> new Query(c.get(0), c.get(1), c.get(2), c.get(3).trim().equals("on_topic"),
                            c.size() > 4 ? c.get(4).trim() : ""));
            String current = support.settings.rawJson("rag.retrieval.min_similarity").orElse("?");

            List<Double> on = new ArrayList<>();
            List<Double> off = new ArrayList<>();
            List<String> khoGap = new ArrayList<>();
            List<String> rows = new ArrayList<>();
            rows.add("topic,level,expected,query,top1_question_id,top1_similarity,note");
            for (Query q : queries) {
                float[] vector = embedding.embed(q.text(), TaskType.RETRIEVAL_QUERY);
                List<SimilarWritingPrompt> top = support.readOnly(
                        () -> support.vectors.nearest(vector, q.topic(), q.level(), 1));
                assertThat(top).as("no active Writing prompt for %s/%s: run the embedding backfill first", q.topic(),
                        q.level()).isNotEmpty();
                double similarity = top.get(0).similarity();
                if (KHO_GAP.equals(q.note())) {
                    // Kho thiếu đề cho truy vấn này: fallback BR-7 là hành vi đúng, không dùng để chỉnh ngưỡng
                    khoGap.add(String.format(Locale.ROOT, "%.3f %s/%s | %s", similarity, q.topic(), q.level(), q.text()));
                } else {
                    (q.onTopic() ? on : off).add(similarity);
                }
                rows.add(String.join(",", RagEvalSupport.csvCell(q.topic()), q.level(),
                        q.onTopic() ? "on_topic" : "off_topic", RagEvalSupport.csvCell(q.text()),
                        top.get(0).questionId().toString(), String.format(Locale.ROOT, "%.4f", similarity),
                        RagEvalSupport.csvCell(q.note())));
            }

            EvalStats.Suggestion s = EvalStats.suggestThreshold(on, off);
            var out = RagEvalSupport.write("threshold.csv", rows);
            System.out.println("AS-7 threshold calibration -> " + out);
            System.out.println("  on_topic : " + EvalStats.distribution(on));
            System.out.println("  off_topic: " + EvalStats.distribution(off));
            System.out.println("  kho_gap (loại khỏi phần tính ngưỡng, n=" + khoGap.size() + "): " + khoGap);
            System.out.println("  current rag.retrieval.min_similarity = " + current);
            System.out.printf(Locale.ROOT, "  suggested threshold = %.3f (separable=%s, accuracy=%.3f, on_topic below=%d,"
                    + " off_topic at/above=%d). NOT applied: update system_setting manually if agreed%n",
                    s.threshold(), s.separable(), s.accuracy(), s.onBelow(), s.offAtLeast());
        }
    }
}
