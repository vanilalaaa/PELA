package com.pela.writing.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * BR-7: đề có sẵn phát cho learner khi không tạo được đề AI. Ưu tiên đề learner chưa làm (chọn ngẫu nhiên); làm hết rồi
 * thì lấy đề làm lâu nhất. Thuần: nguồn ngẫu nhiên truyền vào để test cố định được.
 */
public final class CuratedPromptPolicy {

    private static final Comparator<PromptCandidate> OLDEST_FIRST = Comparator
            .comparing(PromptCandidate::lastDoneAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(PromptCandidate::questionId);

    private CuratedPromptPolicy() {
    }

    /** @param randomIndex nhận n > 0, trả số nguyên trong [0, n) */
    public static Optional<PromptCandidate> choose(List<PromptCandidate> candidates, IntUnaryOperator randomIndex) {
        List<PromptCandidate> notDone = candidates.stream().filter(c -> !c.done()).toList();
        if (!notDone.isEmpty()) {
            return Optional.of(notDone.get(randomIndex.applyAsInt(notDone.size())));
        }
        return candidates.stream().min(OLDEST_FIRST);
    }

    /** Danh sách gợi ý: đề chưa làm trước (giữ thứ tự của kho), rồi đề đã làm, lâu nhất trước; cắt còn {@code limit}. */
    public static List<PromptCandidate> suggestions(List<PromptCandidate> candidates, int limit) {
        List<PromptCandidate> ordered = new ArrayList<>(candidates.stream().filter(c -> !c.done()).toList());
        ordered.addAll(candidates.stream().filter(PromptCandidate::done).sorted(OLDEST_FIRST).toList());
        return ordered.stream().limit(Math.max(0, limit)).toList();
    }
}
