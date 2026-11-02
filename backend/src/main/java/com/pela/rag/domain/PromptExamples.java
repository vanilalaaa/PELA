package com.pela.rag.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Dựng các biến prompt từ Top-K đề mẫu và tính ràng buộc dạng bài / khoảng từ cho đề do LLM sinh ra. */
public final class PromptExamples {

    private PromptExamples() {
    }

    /** Biến {{examples}}: mỗi đề một dòng đánh số từ 1. */
    public static String render(List<PromptExample> examples) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < examples.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(examples.get(i).render(i + 1));
        }
        return sb.toString();
    }

    /**
     * Biến {{task_type_rule}}. BR-24: khoá cache gồm dạng bài. Learner không bắt buộc chọn dạng bài, nên khi không chọn
     * thì LLM giữ dạng bài của đề mẫu đầu tiên (Top-1).
     */
    public static String taskTypeRule(Optional<WritingTaskType> requested) {
        return requested.map(type -> "the task type must be " + type.dbValue())
                .orElse("keep the task type of the first example");
    }

    /** Các dạng bài cho phép với đề sinh ra: dạng learner chọn, hoặc các dạng có trong đề mẫu (rỗng = không ràng buộc). */
    public static Set<WritingTaskType> allowedTypes(Optional<WritingTaskType> requested, List<PromptExample> examples) {
        if (requested.isPresent()) {
            return EnumSet.of(requested.get());
        }
        Set<WritingTaskType> types = examples.stream()
                .map(e -> WritingTaskType.parse(e.taskType()))
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(WritingTaskType.class)));
        return types;
    }

    /** Khoảng từ bao các đề mẫu: min nhỏ nhất, max lớn nhất của những đề có ghi đủ hai giá trị; rỗng nếu không đề nào có. */
    public static Optional<WordRange> wordRange(List<PromptExample> examples) {
        Integer min = null;
        Integer max = null;
        for (PromptExample e : examples) {
            if (e.minWords() == null || e.maxWords() == null) {
                continue;
            }
            min = min == null ? e.minWords() : Math.min(min, e.minWords());
            max = max == null ? e.maxWords() : Math.max(max, e.maxWords());
        }
        return min == null ? Optional.empty() : Optional.of(new WordRange(min, max));
    }

    public record WordRange(int min, int max) {
    }
}
