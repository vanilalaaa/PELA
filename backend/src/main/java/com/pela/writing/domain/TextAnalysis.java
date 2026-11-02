package com.pela.writing.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiểm tra theo luật trước khi gọi AI (Writing.Grade.Pipeline, UC-23 bước 1): tách câu, đếm từ, tìm từ
 * nội dung lặp. Thuần Java, kết quả tất định.
 */
public final class TextAnalysis {

    /**
     * Một từ: chuỗi chữ/số, có thể nối bằng ' ’ hoặc - (it's, well-known). Cùng quy tắc với
     * {@code countWords} ở frontend/packages/domain (test vector: packages/fixtures/word-count-cases.json).
     */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*");

    /** Viết tắt hay gặp có dấu chấm nhưng không kết thúc câu. */
    private static final Set<String> ABBREVIATIONS = Set.of(
            "mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr", "vs", "etc", "e.g", "i.e", "a.m", "p.m", "no");

    private TextAnalysis() {
    }

    public static int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Matcher matcher = WORD.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Tách câu theo . ! ? (kể cả dấu đóng ngoặc/nháy theo sau) hoặc xuống dòng; offset theo văn bản gốc. */
    public static List<Sentence> splitSentences(String text) {
        List<Sentence> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sentences;
        }
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int end = -1;
            if (c == '\n' || c == '\r') {
                end = i;
            } else if ((c == '.' || c == '!' || c == '?') && !isAbbreviation(text, i)) {
                int j = i + 1;
                while (j < text.length() && ".!?\"'’”)".indexOf(text.charAt(j)) >= 0) {
                    j++;
                }
                if (j == text.length() || Character.isWhitespace(text.charAt(j))) {
                    end = j;
                    i = j - 1;
                }
            }
            if (end >= 0) {
                add(sentences, text, start, end);
                start = end;
            }
            i++;
        }
        add(sentences, text, start, text.length());
        return sentences;
    }

    /** Từ nội dung (không phải từ chức năng, dài ≥ 3, không phải số) xuất hiện ≥ minCount lần; nhiều nhất trước. */
    public static List<RepeatedWord> repeatedContentWords(String text, int minCount) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (text != null) {
            Matcher matcher = WORD.matcher(text);
            while (matcher.find()) {
                String word = matcher.group().toLowerCase(Locale.ROOT).replace('’', '\'');
                if (word.length() >= 3 && !FunctionWords.contains(word) && !word.chars().allMatch(Character::isDigit)) {
                    counts.merge(word, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= minCount)
                .map(e -> new RepeatedWord(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(RepeatedWord::count).reversed().thenComparing(RepeatedWord::word))
                .toList();
    }

    private static void add(List<Sentence> sentences, String text, int from, int to) {
        int s = from;
        int e = to;
        while (s < e && Character.isWhitespace(text.charAt(s))) {
            s++;
        }
        while (e > s && Character.isWhitespace(text.charAt(e - 1))) {
            e--;
        }
        if (e > s) {
            sentences.add(new Sentence(sentences.size(), text.substring(s, e), s, e));
        }
    }

    private static boolean isAbbreviation(String text, int dotIndex) {
        if (text.charAt(dotIndex) != '.') {
            return false;
        }
        int k = dotIndex - 1;
        while (k >= 0 && (Character.isLetter(text.charAt(k)) || text.charAt(k) == '.')) {
            k--;
        }
        String token = text.substring(k + 1, dotIndex).toLowerCase(Locale.ROOT);
        return ABBREVIATIONS.contains(token);
    }

    /** Một câu và vị trí của nó trong văn bản gốc ([start, end)). */
    public record Sentence(int index, String text, int start, int end) {
    }

    public record RepeatedWord(String word, int count) {
    }
}
