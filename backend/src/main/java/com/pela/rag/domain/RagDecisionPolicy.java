package com.pela.rag.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Quyết định thuần của UC-24 (không mạng, không DB): có đủ gần để gọi LLM không, đề sinh ra có hợp lệ không.
 * Mọi ngưỡng truyền vào từ system_setting; không hard-code ở đây (trừ giới hạn nghiệp vụ Writing.Submit.Limit).
 */
public final class RagDecisionPolicy {

    /** Writing.Submit.Limit: bài nộp 20–300 từ nên khoảng từ của đề phải nằm trong đó. */
    public static final int MIN_WORDS_FLOOR = 20;
    public static final int MAX_WORDS_LIMIT = 300;
    /** Đề là 1–3 câu ngắn; chặn đầu ra quá dài (có thể là lời giải thích hay nhiều đề). */
    static final int MAX_PROMPT_CHARS = 600;

    private RagDecisionPolicy() {
    }

    /** BR-8: chỉ gọi LLM khi Top-1 similarity >= ngưỡng. Không có Top-1 (kho trống) thì không đạt. */
    public static boolean meetsRetrievalThreshold(Optional<Double> top1Similarity, double minSimilarity) {
        return top1Similarity.isPresent() && top1Similarity.get() >= minSimilarity;
    }

    /**
     * RAG.Generate.Validate: dạng bài thuộc dạng cho phép, khoảng từ hợp lệ và nằm trong khoảng của đề mẫu, nội dung
     * không rỗng và không quá dài. Rỗng = hợp lệ; ngược lại là {@link FallbackReason#INVALID_OUTPUT}.
     *
     * @param allowedTypes dạng bài cho phép; rỗng = không ràng buộc (đề mẫu không ghi dạng bài)
     * @param exampleRange khoảng từ bao các đề mẫu; rỗng = chỉ kiểm giới hạn 20–300
     */
    public static Optional<FallbackReason> validate(GeneratedPrompt generated, Set<WritingTaskType> allowedTypes,
            Optional<PromptExamples.WordRange> exampleRange) {
        if (generated == null || generated.promptText() == null || generated.promptText().isBlank()
                || generated.promptText().strip().length() > MAX_PROMPT_CHARS) {
            return invalid();
        }
        Optional<WritingTaskType> type = WritingTaskType.parse(generated.taskType());
        if (type.isEmpty() || (!allowedTypes.isEmpty() && !allowedTypes.contains(type.get()))) {
            return invalid();
        }
        int min = generated.minWords();
        int max = generated.maxWords();
        if (min < MIN_WORDS_FLOOR || min >= max || max > MAX_WORDS_LIMIT) {
            return invalid();
        }
        if (exampleRange.isPresent() && (min < exampleRange.get().min() || max > exampleRange.get().max())) {
            return invalid();
        }
        return Optional.empty();
    }

    /**
     * Đề sinh ra giống đề kho cùng topic + level >= {@code maxBankSimilarity} thì coi là chép đề mẫu (rag.generation.
     * max_bank_similarity). Không so được (không có đề kho) thì không loại.
     */
    public static Optional<FallbackReason> checkBankSimilarity(Optional<Double> mostSimilarBankPrompt,
            double maxBankSimilarity) {
        return mostSimilarBankPrompt.filter(similarity -> similarity >= maxBankSimilarity).map(s -> FallbackReason.INVALID_OUTPUT);
    }

    private static Optional<FallbackReason> invalid() {
        return Optional.of(FallbackReason.INVALID_OUTPUT);
    }
}
