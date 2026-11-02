package com.pela.shared.domain.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * So khớp từ/cụm từ cấm theo ranh giới từ Unicode (BR-15): "ass" không khớp "class", "đụ" không khớp
 * "đụng". Không phân biệt hoa thường; chuẩn hoá NFC để tiếng Việt dựng sẵn và tổ hợp khớp như nhau.
 * Cụm nhiều từ khớp với mọi khoảng trắng ở giữa.
 */
public final class WordBlocklist {

    // Ranh giới: không đứng cạnh chữ cái, dấu kết hợp (dấu tiếng Việt) hay chữ số
    private static final String BEFORE = "(?<![\\p{L}\\p{M}\\p{N}])";
    private static final String AFTER = "(?![\\p{L}\\p{M}\\p{N}])";

    private final List<Term> terms;

    private WordBlocklist(List<Term> terms) {
        this.terms = terms;
    }

    /** Mỗi phần tử là một từ hoặc cụm từ; dòng trống và dòng bắt đầu bằng '#' bị bỏ qua. */
    public static WordBlocklist of(Collection<String> entries) {
        List<Term> terms = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String entry : entries) {
            String term = normalize(entry).trim();
            if (term.isEmpty() || term.startsWith("#") || !seen.add(term)) {
                continue;
            }
            String body = String.join("\\s+", Arrays.stream(term.split("\\s+")).map(Pattern::quote).toList());
            terms.add(new Term(term,
                    Pattern.compile(BEFORE + body + AFTER, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE)));
        }
        return new WordBlocklist(List.copyOf(terms));
    }

    /** Các từ cấm (dạng đã chuẩn hoá) xuất hiện trong văn bản, theo thứ tự trong danh sách. */
    public List<String> findMatches(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = normalize(text);
        List<String> matches = new ArrayList<>();
        for (Term term : terms) {
            if (term.pattern().matcher(normalized).find()) {
                matches.add(term.value());
            }
        }
        return matches;
    }

    public int size() {
        return terms.size();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private record Term(String value, Pattern pattern) {
    }
}
