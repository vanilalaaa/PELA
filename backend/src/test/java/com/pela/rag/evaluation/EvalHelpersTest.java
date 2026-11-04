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

    /**
     * 138 đề Writing = 24 cặp topic + level. Mỗi cặp: 5 dòng on_topic (3 sở thích liên quan hoặc không có, 2 dòng
     * interest_far) và 2 dòng off_topic với filter_topic khác topic. Truy vấn thật dựng bằng RagQueryBuilder.
     */
    @Test
    void queriesFileCoversAll24TopicLevelPairs() throws Exception {
        List<RagEvalSupport.QueryRow> queries = RagEvalSupport.readQueries();

        assertThat(queries).hasSize(168);
        assertThat(queries).filteredOn(RagEvalSupport.QueryRow::onTopic).hasSize(120);
        assertThat(queries).filteredOn(q -> !q.onTopic()).hasSize(48);
        assertThat(queries).filteredOn(RagEvalSupport.QueryRow::interestFar).hasSize(48)
                .allMatch(RagEvalSupport.QueryRow::onTopic);
        assertThat(queries).noneMatch(RagEvalSupport.QueryRow::khoGap); // chưa có truy vấn nào đánh dấu kho_gap

        List<String> pairs = queries.stream().map(q -> q.topic() + "/" + q.level()).distinct().toList();
        assertThat(pairs).hasSize(24);
        for (String pair : pairs) {
            List<RagEvalSupport.QueryRow> rows = queries.stream().filter(q -> (q.topic() + "/" + q.level()).equals(pair))
                    .toList();
            assertThat(rows).as(pair).hasSize(7);
            assertThat(rows.stream().filter(RagEvalSupport.QueryRow::onTopic).count()).as("on_topic of %s", pair).isEqualTo(5);
            assertThat(rows.stream().filter(q -> !q.onTopic()).count()).as("off_topic of %s", pair).isEqualTo(2);
        }
    }

    @Test
    void offTopicRowsFilterByAnotherTopicAndOnTopicRowsByTheirOwn() throws Exception {
        for (RagEvalSupport.QueryRow q : RagEvalSupport.readQueries()) {
            if (q.onTopic()) {
                assertThat(q.filterTopic()).as("%s/%s on_topic", q.topic(), q.level()).isEqualTo(q.topic());
            } else {
                assertThat(q.filterTopic()).as("%s/%s off_topic", q.topic(), q.level()).isNotEqualTo(q.topic());
            }
        }
    }

    @Test
    void queryRowsUseOnlyTheLearnerProfileInterestsAndBuildTheRealQuery() throws Exception {
        List<RagEvalSupport.QueryRow> queries = RagEvalSupport.readQueries();
        java.util.Set<String> allowed = java.util.Set.of("family", "school", "travel", "food", "sport", "music", "movie",
                "tech", "animal", "health");

        assertThat(queries.stream().flatMap(q -> q.interests().stream())).isNotEmpty().allMatch(allowed::contains);
        assertThat(queries.get(0).text()).isEqualTo("Writing prompt. Topic: Travel. CEFR level: A1.");
        assertThat(queries.get(2).text())
                .isEqualTo("Writing prompt. Topic: Travel. CEFR level: A1. Learner interests: travel, food.");
        // Dòng off_topic: truy vấn dựng từ topic của dòng, không từ filter_topic
        RagEvalSupport.QueryRow off = queries.get(5);
        assertThat(off.onTopic()).isFalse();
        assertThat(off.text()).isEqualTo("Writing prompt. Topic: Travel. CEFR level: A1.");
        assertThat(off.filterTopic()).isEqualTo("Work");
    }

    @Test
    void thresholdKeepingOnTopicLosesAtMostOnePercent() {
        List<Double> on = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            on.add(0.60 + i * 0.002); // 0.600 ... 0.798
        }

        // 100 dòng, cho phép mất 1 dòng: ngưỡng = dòng nhỏ thứ hai (0.602)
        assertThat(EvalStats.thresholdKeepingOnTopic(on, 0.99)).isEqualTo(0.602);
        assertThat(EvalStats.passRate(on, 0.602)).isGreaterThanOrEqualTo(0.99);
        assertThat(EvalStats.thresholdKeepingOnTopic(List.of(0.7, 0.8), 0.99)).isEqualTo(0.7);
    }

    @Test
    void passAndBlockedRatesAreComplementsAtTheThresholdInclusive() {
        List<Double> v = List.of(0.60, 0.65, 0.70, 0.75);

        assertThat(EvalStats.passRate(v, 0.65)).isEqualTo(0.75);
        assertThat(EvalStats.blockedRate(v, 0.65)).isEqualTo(0.25);
        assertThat(EvalStats.passRate(List.of(), 0.65)).isEqualTo(1.0);
        assertThat(EvalStats.blockedRate(List.of(), 0.65)).isEqualTo(1.0);
    }
}
