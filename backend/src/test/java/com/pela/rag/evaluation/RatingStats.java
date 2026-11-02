package com.pela.rag.evaluation;

import java.util.List;
import java.util.Locale;

/**
 * PER-3 chất lượng: 2 người chấm độc lập đánh dấu on_topic / right_level (y/n) cho từng đề sinh ra. Một đề "đạt" khi
 * cả hai tiêu chí là y. Tính tỉ lệ đạt của từng người, tỉ lệ đạt khi cả hai cùng cho đạt, và mức đồng thuận
 * (phần trăm trùng + Cohen's kappa).
 */
final class RatingStats {

    private RatingStats() {
    }

    /** Một dòng đã chấm đủ 4 ô. */
    record Rating(boolean onTopic1, boolean rightLevel1, boolean onTopic2, boolean rightLevel2) {

        boolean pass1() {
            return onTopic1 && rightLevel1;
        }

        boolean pass2() {
            return onTopic2 && rightLevel2;
        }
    }

    record Agreement(double percent, double kappa) {

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "agreement=%.3f kappa=%.3f", percent, kappa);
        }
    }

    record Report(int rated, double passRate1, double passRate2, double passRateBoth, Agreement onTopic,
            Agreement rightLevel, Agreement pass) {
    }

    /** y/yes/1/true → true; n/no/0/false → false; khác (vd ô trống) → null. */
    static Boolean parse(String cell) {
        String v = cell == null ? "" : cell.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "y", "yes", "1", "true" -> true;
            case "n", "no", "0", "false" -> false;
            default -> null;
        };
    }

    static Report report(List<Rating> ratings) {
        if (ratings.isEmpty()) {
            throw new IllegalArgumentException("No rated rows");
        }
        int n = ratings.size();
        return new Report(n,
                (double) ratings.stream().filter(Rating::pass1).count() / n,
                (double) ratings.stream().filter(Rating::pass2).count() / n,
                (double) ratings.stream().filter(r -> r.pass1() && r.pass2()).count() / n,
                agreement(ratings.stream().map(r -> new boolean[] {r.onTopic1(), r.onTopic2()}).toList()),
                agreement(ratings.stream().map(r -> new boolean[] {r.rightLevel1(), r.rightLevel2()}).toList()),
                agreement(ratings.stream().map(r -> new boolean[] {r.pass1(), r.pass2()}).toList()));
    }

    /** Cohen's kappa cho 2 người chấm, nhãn nhị phân. Khi kỳ vọng ngẫu nhiên = 1 (cả hai luôn cùng một nhãn) → kappa 1. */
    static Agreement agreement(List<boolean[]> pairs) {
        int n = pairs.size();
        int agree = 0;
        int yes1 = 0;
        int yes2 = 0;
        for (boolean[] p : pairs) {
            agree += p[0] == p[1] ? 1 : 0;
            yes1 += p[0] ? 1 : 0;
            yes2 += p[1] ? 1 : 0;
        }
        double po = (double) agree / n;
        double pe = ((double) yes1 / n) * ((double) yes2 / n) + (1 - (double) yes1 / n) * (1 - (double) yes2 / n);
        double kappa = pe == 1.0 ? 1.0 : (po - pe) / (1 - pe);
        return new Agreement(po, kappa);
    }
}
