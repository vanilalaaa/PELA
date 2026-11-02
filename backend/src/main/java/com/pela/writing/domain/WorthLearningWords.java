package com.pela.writing.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * BR-30: chọn tối đa {@code max} từ đáng học từ ứng viên AI đề xuất. Một từ hợp lệ khi: đến từ lỗi chính tả,
 * dùng sai từ, sai dạng từ, hoặc là từ thay thế cho từ bị lặp; không phải từ chức năng hay tên riêng; không
 * cao hơn trình độ learner quá một bậc CEFR; chưa có trong review pool của learner.
 */
public final class WorthLearningWords {

    private WorthLearningWords() {
    }

    public enum Reason {
        MISSPELLED, MISUSED, WRONG_FORM, BETTER_ALTERNATIVE;

        public String dbValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Reason parse(String value) {
            for (Reason r : values()) {
                if (r.dbValue().equals(value)) {
                    return r;
                }
            }
            return null;
        }
    }

    public record Candidate(String learnerForm, String word, String reason, String meaningVi) {
    }

    /** Từ trong kho chung (vocabulary curated). */
    public record CatalogueWord(UUID vocabId, CefrLevel cefrLevel) {
    }

    /**
     * @param learnerLevel  null = chưa xếp trình độ, bỏ qua luật bậc CEFR
     * @param repeatedWords từ lặp do luật phát hiện (chữ thường)
     * @param catalogue     từ (chữ thường) → mục trong kho chung; không có = từ ngoài kho, bậc CEFR chưa biết
     * @param inReviewPool  từ (chữ thường) learner đã có trong review pool
     */
    public record Context(CefrLevel learnerLevel, Set<String> repeatedWords, Map<String, CatalogueWord> catalogue,
            Set<String> inReviewPool, int max) {
    }

    /** @param catalogueWord có giá trị = từ có sẵn trong kho chung → BR-9 tự đẩy vào hàng ôn */
    public record Selected(String learnerForm, String word, Reason reason, String meaningVi,
            Optional<CatalogueWord> catalogueWord) {

        public CefrLevel cefrLevel() {
            return catalogueWord.map(CatalogueWord::cefrLevel).orElse(null);
        }
    }

    public static List<Selected> select(List<Candidate> candidates, Context context) {
        List<Selected> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (selected.size() >= context.max()) {
                break;
            }
            Reason reason = Reason.parse(candidate.reason());
            String word = candidate.word() == null ? "" : candidate.word().trim();
            String learnerForm = candidate.learnerForm() == null ? "" : candidate.learnerForm().trim();
            String key = word.toLowerCase(Locale.ROOT);
            if (reason == null || word.isEmpty() || candidate.meaningVi() == null || candidate.meaningVi().isBlank()) {
                continue;
            }
            if (reason == Reason.BETTER_ALTERNATIVE
                    && !context.repeatedWords().contains(learnerForm.toLowerCase(Locale.ROOT))) {
                continue;
            }
            // Trùng với chính từ learner đã viết thì không có gì để học
            if (isFunctionWordOrPhrase(word) || isProperNoun(word) || key.equals(learnerForm.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Optional<CatalogueWord> catalogueWord = Optional.ofNullable(context.catalogue().get(key));
            if (catalogueWord.isPresent() && !withinOneLevel(catalogueWord.get().cefrLevel(), context.learnerLevel())) {
                continue;
            }
            if (context.inReviewPool().contains(key) || !seen.add(key)) {
                continue;
            }
            selected.add(new Selected(learnerForm, word, reason, candidate.meaningVi().trim(), catalogueWord));
        }
        return selected;
    }

    private static boolean isFunctionWordOrPhrase(String word) {
        for (String token : word.split("\\s+")) {
            if (!FunctionWords.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /** Tên riêng: viết hoa chữ đầu (trừ "I" và từ viết hoa toàn bộ như "OK"). */
    private static boolean isProperNoun(String word) {
        char first = word.charAt(0);
        return Character.isUpperCase(first) && word.length() > 1 && !word.equals(word.toUpperCase(Locale.ROOT));
    }

    private static boolean withinOneLevel(CefrLevel wordLevel, CefrLevel learnerLevel) {
        return wordLevel == null || learnerLevel == null || wordLevel.ordinal() <= learnerLevel.ordinal() + 1;
    }
}
