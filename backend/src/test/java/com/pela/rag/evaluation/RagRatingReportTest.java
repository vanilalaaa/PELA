package com.pela.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PER-3 chất lượng: đọc file đã được 2 người chấm (mặc định docs/evaluation/rag/generated.csv, đổi bằng biến
 * RAG_RATED_CSV), tính tỉ lệ đạt và mức đồng thuận. Không gọi DB hay Gemini. Dòng chưa chấm đủ 4 ô bị báo lỗi.
 */
@Tag("external")
class RagRatingReportTest {

    @Test
    void passRateAndAgreement() throws Exception {
        String override = RagEvalSupport.config("RAG_RATED_CSV");
        Path file = override != null ? Path.of(override) : RagEvalSupport.EVAL_DIR.resolve("generated.csv");
        List<String> incomplete = new ArrayList<>();
        List<RatingStats.Rating> ratings = new ArrayList<>();
        for (List<String> c : RagEvalSupport.readCsv(file, c -> c)) {
            Boolean ot1 = c.size() > 4 ? RatingStats.parse(c.get(4)) : null;
            Boolean rl1 = c.size() > 5 ? RatingStats.parse(c.get(5)) : null;
            Boolean ot2 = c.size() > 6 ? RatingStats.parse(c.get(6)) : null;
            Boolean rl2 = c.size() > 7 ? RatingStats.parse(c.get(7)) : null;
            if (ot1 == null || rl1 == null || ot2 == null || rl2 == null) {
                incomplete.add(c.get(0));
            } else {
                ratings.add(new RatingStats.Rating(ot1, rl1, ot2, rl2));
            }
        }
        Assumptions.assumeFalse(ratings.isEmpty() && incomplete.isEmpty(), "No generated prompts in " + file);
        assertThat(incomplete).as("rows not fully rated (need y/n in all 4 rating columns)").isEmpty();

        RatingStats.Report r = RatingStats.report(ratings);
        System.out.println("PER-3 manual review: " + file.toAbsolutePath());
        System.out.printf(Locale.ROOT, "  rated = %d (PER-3 sample = 50)%n", r.rated());
        System.out.printf(Locale.ROOT, "  pass rate rater 1 = %.3f, rater 2 = %.3f, both = %.3f (target >= 0.90)%n",
                r.passRate1(), r.passRate2(), r.passRateBoth());
        System.out.println("  on_topic   " + r.onTopic());
        System.out.println("  right_level " + r.rightLevel());
        System.out.println("  pass       " + r.pass());
    }
}
