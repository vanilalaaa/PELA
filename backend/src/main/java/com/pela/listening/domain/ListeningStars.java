package com.pela.listening.domain;

import java.util.Comparator;
import java.util.List;

/** Sao của phiên nghe theo độ chính xác (BR-16) và phần được cộng khi làm lại bài (BR-36). */
public final class ListeningStars {

    private ListeningStars() {
    }

    /** Một bậc của setting {@code star.bands}: đạt ít nhất {@code minPct}% thì được {@code stars} sao. */
    public record Band(double minPct, int stars) {
    }

    /** BR-16: lấy bậc cao nhất mà tỉ lệ đúng đạt tới; không đạt bậc nào thì 0 sao. */
    public static int rate(int correct, int total, List<Band> bands) {
        if (total <= 0) {
            throw new IllegalArgumentException("total must be positive");
        }
        double pct = 100.0 * correct / total;
        return bands.stream()
                .sorted(Comparator.comparingDouble(Band::minPct).reversed())
                .filter(b -> pct >= b.minPct())
                .mapToInt(Band::stars)
                .findFirst()
                .orElse(0);
    }

    /** BR-36: chỉ cộng phần vượt kết quả tốt nhất trước đó của bài nghe này. */
    public static int added(int stars, int previousBest) {
        return Math.max(0, stars - Math.max(0, previousBest));
    }
}
