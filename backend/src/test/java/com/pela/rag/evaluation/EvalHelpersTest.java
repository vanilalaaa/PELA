package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit test cho công cụ đánh giá RAG (chạy trong build, không cần DB/Gemini). */
class EvalHelpersTest {

    @Test
    void percentileUsesNearestRank() {
        List<Double> v = List.of(5.0, 1.0, 3.0, 2.0, 4.0, 6.0, 7.0, 8.0, 9.0, 10.0,
                11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0);

        assertThat(EvalStats.percentile(v, 95)).isEqualTo(19.0);
        assertThat(EvalStats.percentile(v, 50)).isEqualTo(10.0);
        assertThat(EvalStats.percentile(v, 0)).isEqualTo(1.0);
        assertThat(EvalStats.percentile(v, 100)).isEqualTo(20.0);
    }

    @Test
    void separableGroupsGetMidpointThreshold() {
        var s = EvalStats.suggestThreshold(List.of(0.688, 0.72, 0.754), List.of(0.589, 0.61));

        assertThat(s.separable()).isTrue();
        assertThat(s.threshold()).isEqualTo(0.649);
        assertThat(s.accuracy()).isEqualTo(1.0);
    }

    @Test
    void overlappingGroupsGetMostAccurateThresholdPreferringFewerOnTopicMisses() {
        var s = EvalStats.suggestThreshold(List.of(0.60, 0.70, 0.80), List.of(0.50, 0.65));

        assertThat(s.separable()).isFalse();
        // 0.60 và 0.70 cùng độ chính xác 0.8; chọn 0.60 vì không loại đề on_topic nào
        assertThat(s.threshold()).isEqualTo(0.60);
        assertThat(s.onBelow()).isZero();
        assertThat(s.offAtLeast()).isEqualTo(1);
        assertThat(s.accuracy()).isCloseTo(0.8, within(1e-9));
    }

    @Test
    void ratingParsesYesNo() {
        assertThat(RatingStats.parse(" Y ")).isTrue();
        assertThat(RatingStats.parse("no")).isFalse();
        assertThat(RatingStats.parse("")).isNull();
    }

    @Test
    void passRateAndKappa() {
        List<RatingStats.Rating> r = List.of(
                new RatingStats.Rating(true, true, true, true),
                new RatingStats.Rating(true, true, true, true),
                new RatingStats.Rating(true, false, true, true),
                new RatingStats.Rating(false, true, false, true));

        RatingStats.Report report = RatingStats.report(r);

        assertThat(report.passRate1()).isEqualTo(0.5);
        assertThat(report.passRate2()).isEqualTo(0.75);
        assertThat(report.passRateBoth()).isEqualTo(0.5);
        assertThat(report.onTopic().percent()).isEqualTo(1.0);
        assertThat(report.onTopic().kappa()).isEqualTo(1.0);
        // pass: (y,y),(y,y),(n,y),(n,n) → po = 0.75, pe = 0.5*0.75 + 0.5*0.25 = 0.5, kappa = 0.5
        assertThat(report.pass().percent()).isEqualTo(0.75);
        assertThat(report.pass().kappa()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void recallAt5() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        assertThat(RagHnswRecallTest.recall(List.of(a, b), List.of(a, b))).isEqualTo(1.0);
        assertThat(RagHnswRecallTest.recall(List.of(a, c), List.of(a, b))).isEqualTo(0.5);
        assertThat(RagHnswRecallTest.recall(List.of(), List.of())).isEqualTo(1.0);
    }

    @Test
    void csvLineHandlesQuotedCommas() {
        assertThat(RagEvalSupport.parseCsvLine("Travel,A2,\"Bus or car, which is better?\",on_topic"))
                .containsExactly("Travel", "A2", "Bus or car, which is better?", "on_topic");
    }

    /** 138 đề Writing = 24 cặp topic + level (A1–B1: 4 chủ đề; B2–C2: 4 chủ đề). Mỗi cặp cần cả hai nhóm truy vấn. */
    @Test
    void queriesFileCoversAll24TopicLevelPairs() throws Exception {
        List<String> rows = RagEvalSupport.readCsv(RagEvalSupport.EVAL_DIR.resolve("queries.csv"),
                c -> c.get(0) + "/" + c.get(1) + "/" + c.get(3));

        assertThat(rows).filteredOn(r -> r.endsWith("/on_topic")).hasSize(96);
        assertThat(rows).filteredOn(r -> r.endsWith("/off_topic")).hasSize(37);

        List<String> pairs = rows.stream().map(r -> r.substring(0, r.lastIndexOf('/'))).distinct().toList();
        assertThat(pairs).hasSize(24);
        for (String pair : pairs) {
            assertThat(rows.stream().filter(r -> r.equals(pair + "/on_topic")).count())
                    .as("on_topic of %s", pair).isGreaterThanOrEqualTo(2);
            assertThat(rows.stream().filter(r -> r.equals(pair + "/off_topic")).count())
                    .as("off_topic of %s", pair).isGreaterThanOrEqualTo(1);
        }
    }
}
