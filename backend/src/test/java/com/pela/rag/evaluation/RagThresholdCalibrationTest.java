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
 * AS-7 / BR-8: đo similarity Top-1 cho từng truy vấn trong docs/evaluation/rag/queries.csv, ghi
 * build/evaluation/rag/threshold.csv, in phân bố và khuyến nghị ngưỡng. KHÔNG sửa system_setting: chỉ đề xuất.
 *
 * <ul>
 *   <li>Truy vấn dựng bằng {@link com.pela.rag.domain.RagQueryBuilder} từ topic + level + sở thích (đúng hàm UC-24), truy
 *       hồi lọc theo {@code filter_topic} + level (trống = topic).</li>
 *   <li>on_topic: yêu cầu bình thường, hệ thống nên tạo đề (kể cả dòng {@code interest_far}: sở thích chỉ để cá nhân hoá,
 *       topic do learner chọn). off_topic: truy vấn của topic A nhưng kho lọc theo topic B, mô phỏng kho không có đề sát
 *       yêu cầu, hệ thống nên chuyển sang đề có sẵn.</li>
 *   <li>Ngưỡng đề xuất ưu tiên không loại nhầm người học: lớn nhất sao cho &ge; 99% on_topic được tạo đề.</li>
 * </ul>
 */
@Tag("external")
class RagThresholdCalibrationTest {

    private static final double MIN_ON_TOPIC_PASS_RATE = 0.99;
    private static final double[] REPORT_LEVELS = {0.60, 0.65, 0.68, 0.70};

    @Test
    void calibrateRetrievalThreshold() throws Exception {
        try (RagEvalSupport support = RagEvalSupport.connect()) {
            EmbeddingPort embedding = support.embedding();
            List<RagEvalSupport.QueryRow> queries = RagEvalSupport.readQueries();
            String current = support.settings.rawJson("rag.retrieval.min_similarity").orElse("?");

            List<Double> onTopic = new ArrayList<>(); // mọi on_topic (gồm interest_far) trừ kho_gap: dùng để chọn ngưỡng
            List<Double> plainOn = new ArrayList<>();
            List<Double> interestFar = new ArrayList<>();
            List<Double> offTopic = new ArrayList<>();
            List<String> khoGap = new ArrayList<>();
            List<String> rows = new ArrayList<>();
            rows.add("topic,level,interests,filter_topic,expected,query,top1_question_id,top1_similarity,note");
            for (RagEvalSupport.QueryRow q : queries) {
                // Truy vấn dựng bằng RagQueryBuilder: đúng hàm UC-24 dùng (RAG.Embed.Input)
                float[] vector = embedding.embed(q.text(), TaskType.RETRIEVAL_QUERY);
                List<SimilarWritingPrompt> top = support.readOnly(
                        () -> support.vectors.nearest(vector, q.filterTopic(), q.level(), 1));
                assertThat(top).as("no active Writing prompt for %s/%s: run the embedding backfill first",
                        q.filterTopic(), q.level()).isNotEmpty();
                double similarity = top.get(0).similarity();
                if (q.khoGap()) {
                    // Kho thiếu đề cho truy vấn này: fallback BR-7 là hành vi đúng, không dùng để chỉnh ngưỡng
                    khoGap.add(String.format(Locale.ROOT, "%.3f %s/%s | %s", similarity, q.topic(), q.level(), q.text()));
                } else if (q.onTopic()) {
                    onTopic.add(similarity);
                    (q.interestFar() ? interestFar : plainOn).add(similarity);
                } else {
                    offTopic.add(similarity);
                }
                rows.add(String.join(",", RagEvalSupport.csvCell(q.topic()), q.level(),
                        RagEvalSupport.csvCell(String.join("|", q.interests())),
                        RagEvalSupport.csvCell(q.topic().equals(q.filterTopic()) ? "" : q.filterTopic()),
                        q.onTopic() ? "on_topic" : "off_topic", RagEvalSupport.csvCell(q.text()),
                        top.get(0).questionId().toString(), String.format(Locale.ROOT, "%.4f", similarity),
                        RagEvalSupport.csvCell(q.note())));
            }

            double suggested = EvalStats.thresholdKeepingOnTopic(onTopic, MIN_ON_TOPIC_PASS_RATE);
            var out = RagEvalSupport.write("threshold.csv", rows);
            System.out.println("AS-7 threshold calibration -> " + out);
            System.out.println("  on_topic (plain)       : " + EvalStats.distribution(plainOn));
            System.out.println("  on_topic (interest_far): " + EvalStats.distribution(interestFar)
                    + "  <- sở thích xa topic, vẫn là yêu cầu bình thường");
            System.out.println("  off_topic (filter_topic khác): " + EvalStats.distribution(offTopic));
            System.out.println("  kho_gap (loại khỏi phần tính ngưỡng, n=" + khoGap.size() + "): " + khoGap);
            System.out.println("  current rag.retrieval.min_similarity = " + current);
            System.out.println("  threshold | on_topic pass | interest_far pass | off_topic blocked");
            for (double t : REPORT_LEVELS) {
                System.out.println(row(t, onTopic, interestFar, offTopic));
            }
            System.out.printf(Locale.ROOT, "  suggested threshold = %.3f (largest keeping >= %.0f%% of on_topic)%n%s%n",
                    suggested, MIN_ON_TOPIC_PASS_RATE * 100, row(suggested, onTopic, interestFar, offTopic));
            System.out.println("  NOT applied: update system_setting manually if agreed");
        }
    }

    private static String row(double t, List<Double> onTopic, List<Double> interestFar, List<Double> offTopic) {
        return String.format(Locale.ROOT, "  %9.3f | %12.1f%% | %16.1f%% | %16.1f%%", t,
                EvalStats.passRate(onTopic, t) * 100, EvalStats.passRate(interestFar, t) * 100,
                EvalStats.blockedRate(offTopic, t) * 100);
    }
}
