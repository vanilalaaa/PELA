package com.pela.writing.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Kiểm tra và hoàn thiện kết quả AI (UC-23 bước 4–5, Writing.Grade.Validate, BR-18): định vị issue trong văn bản
 * gốc (không định vị được thì bỏ), ép style thành gợi ý, tự cộng điểm, quy đổi sao.
 */
public final class WritingGrading {

    private WritingGrading() {
    }

    public enum IssueCategory {
        GRAMMAR, SPELLING, WORD_CHOICE, REPETITION, PUNCTUATION, STYLE;

        public String dbValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static IssueCategory parse(String value) {
            for (IssueCategory c : values()) {
                if (c.dbValue().equals(value)) {
                    return c;
                }
            }
            return null;
        }
    }

    public enum Severity {
        ERROR, SUGGESTION;

        public String dbValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Issue AI trả về, chưa kiểm tra. */
    public record AiIssue(int sentenceIndex, String original, String suggestion, String category, String severity,
            String explanationVi) {
    }

    /** Issue đã định vị: [start, end) là offset trong văn bản gốc do server tính, không tin offset của AI. */
    public record LocatedIssue(int sentenceIndex, int start, int end, String original, String suggestion,
            IssueCategory category, Severity severity, String explanationVi) {
    }

    /** Bốn tiêu chí 0–25 (Writing.Grade.Output). */
    public record CriterionScores(int taskAchievement, int coherence, int vocabulary, int grammar) {

        /** Tổng 0–100 tự cộng, không dùng tổng của AI; mỗi tiêu chí bị kẹp vào 0–25. */
        public int total() {
            return clamp(taskAchievement) + clamp(coherence) + clamp(vocabulary) + clamp(grammar);
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(25, value));
        }
    }

    /** Một mốc quy đổi sao (setting star.writing_bands). */
    public record StarBand(int minScore, int stars) {
    }

    /** BR-18: số sao của mốc cao nhất mà điểm đạt tới; không đạt mốc nào → 0. */
    public static int stars(int score, List<StarBand> bands) {
        int best = 0;
        int bestMin = Integer.MIN_VALUE;
        for (StarBand band : bands) {
            if (score >= band.minScore() && band.minScore() > bestMin) {
                best = band.stars();
                bestMin = band.minScore();
            }
        }
        return best;
    }

    /**
     * Định vị từng issue trong câu tương ứng (khớp nguyên văn, rồi không phân biệt hoa thường). Nếu sentence_index
     * sai, chấp nhận khi đoạn trích xuất hiện đúng một lần trong cả bài. Không định vị được, danh mục lạ hoặc trùng
     * vị trí → bỏ. Lỗi style luôn là gợi ý.
     */
    public static List<LocatedIssue> locate(String text, List<TextAnalysis.Sentence> sentences, List<AiIssue> issues) {
        List<LocatedIssue> located = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiIssue issue : issues) {
            IssueCategory category = IssueCategory.parse(issue.category());
            String original = issue.original();
            if (category == null || original == null || original.isBlank()) {
                continue;
            }
            int[] span = findInSentence(sentences, issue.sentenceIndex(), original);
            if (span == null) {
                span = findUniqueInText(text, sentences, original);
            }
            if (span == null) {
                continue;
            }
            Severity severity = category == IssueCategory.STYLE || "suggestion".equals(issue.severity())
                    ? Severity.SUGGESTION : Severity.ERROR;
            if (seen.add(span[1] + ":" + span[2] + ":" + category)) {
                located.add(new LocatedIssue(span[0], span[1], span[2], text.substring(span[1], span[2]),
                        issue.suggestion() == null ? "" : issue.suggestion(), category, severity,
                        issue.explanationVi() == null ? "" : issue.explanationVi()));
            }
        }
        return located;
    }

    /** {sentenceIndex, start, end} hoặc null. */
    private static int[] findInSentence(List<TextAnalysis.Sentence> sentences, int index, String original) {
        if (index < 0 || index >= sentences.size()) {
            return null;
        }
        TextAnalysis.Sentence sentence = sentences.get(index);
        int at = sentence.text().indexOf(original);
        if (at < 0) {
            at = sentence.text().toLowerCase(Locale.ROOT).indexOf(original.toLowerCase(Locale.ROOT));
        }
        return at < 0 ? null : new int[] {index, sentence.start() + at, sentence.start() + at + original.length()};
    }

    private static int[] findUniqueInText(String text, List<TextAnalysis.Sentence> sentences, String original) {
        int first = text.indexOf(original);
        if (first < 0 || text.indexOf(original, first + 1) >= 0) {
            return null;
        }
        for (TextAnalysis.Sentence sentence : sentences) {
            if (first >= sentence.start() && first + original.length() <= sentence.end()) {
                return new int[] {sentence.index(), first, first + original.length()};
            }
        }
        return null;
    }
}
