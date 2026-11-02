package com.pela.rag.evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Thống kê dùng chung cho đánh giá RAG (thuần Java, có unit test). */
final class EvalStats {

    private EvalStats() {
    }

    /** Percentile theo nearest-rank: phần tử thứ ceil(p/100 × n) trong dãy đã sắp xếp. */
    static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("No values");
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    /** min / p25 / median / p75 / max, 3 chữ số thập phân. */
    static String distribution(List<Double> values) {
        if (values.isEmpty()) {
            return "n=0";
        }
        return String.format(Locale.ROOT, "n=%d min=%.3f p25=%.3f median=%.3f p75=%.3f max=%.3f", values.size(),
                percentile(values, 0), percentile(values, 25), percentile(values, 50), percentile(values, 75),
                percentile(values, 100));
    }

    /**
     * @param threshold  ngưỡng đề xuất: on_topic khi similarity ≥ threshold
     * @param separable  true = mọi on_topic đều cao hơn mọi off_topic (threshold ở giữa khoảng trống)
     * @param onBelow    số truy vấn on_topic bị loại (similarity &lt; threshold)
     * @param offAtLeast số truy vấn off_topic lọt qua (similarity ≥ threshold)
     */
    record Suggestion(double threshold, boolean separable, double accuracy, int onBelow, int offAtLeast) {
    }

    /**
     * Ngưỡng tách hai nhóm: nếu tách được hoàn toàn thì lấy điểm giữa max(off) và min(on); nếu không, chọn ngưỡng có
     * độ chính xác cao nhất (hoà thì ưu tiên ít on_topic bị loại — BR-7 vẫn có đề dự phòng).
     */
    static Suggestion suggestThreshold(List<Double> onTopic, List<Double> offTopic) {
        if (onTopic.isEmpty() || offTopic.isEmpty()) {
            throw new IllegalArgumentException("Need both on_topic and off_topic values");
        }
        double minOn = Collections.min(onTopic);
        double maxOff = Collections.max(offTopic);
        if (minOn > maxOff) {
            double t = round((minOn + maxOff) / 2);
            return new Suggestion(t, true, 1.0, 0, 0);
        }
        List<Double> candidates = new ArrayList<>();
        candidates.addAll(onTopic);
        candidates.addAll(offTopic);
        Suggestion best = null;
        for (double t : candidates) {
            int onBelow = (int) onTopic.stream().filter(v -> v < t).count();
            int offAtLeast = (int) offTopic.stream().filter(v -> v >= t).count();
            double accuracy = 1.0 - (double) (onBelow + offAtLeast) / (onTopic.size() + offTopic.size());
            Suggestion s = new Suggestion(round(t), false, accuracy, onBelow, offAtLeast);
            if (best == null || accuracy > best.accuracy()
                    || (accuracy == best.accuracy() && onBelow < best.onBelow())) {
                best = s;
            }
        }
        return best;
    }

    /**
     * Ngưỡng ưu tiên KHÔNG loại nhầm người học: giá trị lớn nhất mà ít nhất {@code minPassRate} (vd 0.99) dòng on_topic có
     * similarity >= ngưỡng. Làm tròn xuống 3 chữ số để không loại thêm.
     */
    static double thresholdKeepingOnTopic(List<Double> onTopic, double minPassRate) {
        if (onTopic.isEmpty()) {
            throw new IllegalArgumentException("Need on_topic values");
        }
        List<Double> sorted = new ArrayList<>(onTopic);
        Collections.sort(sorted);
        int allowedLoss = (int) Math.floor((1 - minPassRate) * sorted.size() + 1e-9);
        return Math.floor(sorted.get(Math.min(allowedLoss, sorted.size() - 1)) * 1000) / 1000.0;
    }

    /** Tỉ lệ giá trị >= ngưỡng (số dòng đi qua ngưỡng, tức là được AI tạo đề). 1.0 khi danh sách rỗng. */
    static double passRate(List<Double> values, double threshold) {
        return values.isEmpty() ? 1.0 : (double) values.stream().filter(v -> v >= threshold).count() / values.size();
    }

    /** Tỉ lệ giá trị < ngưỡng (số dòng bị chặn, chuyển sang đề có sẵn). 1.0 khi danh sách rỗng. */
    static double blockedRate(List<Double> values, double threshold) {
        return values.isEmpty() ? 1.0 : 1.0 - passRate(values, threshold);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
