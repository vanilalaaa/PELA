package com.pela.writing.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Từ chức năng tiếng Anh (mạo từ, giới từ, đại từ, trợ động từ, liên từ…): không tính là từ nội dung khi
 * tìm từ lặp và không được gợi ý làm từ đáng học (BR-30). Dữ liệu ngôn ngữ, không phải ngưỡng cấu hình.
 */
public final class FunctionWords {

    private static final Set<String> WORDS = Set.of(
            "a", "an", "the", "this", "that", "these", "those", "some", "any", "each", "every", "no", "all", "both",
            "either", "neither", "much", "many", "more", "most", "few", "little", "less", "other", "another", "such",
            "i", "me", "my", "mine", "myself", "you", "your", "yours", "yourself", "yourselves", "he", "him", "his",
            "himself", "she", "her", "hers", "herself", "it", "its", "itself", "we", "us", "our", "ours", "ourselves",
            "they", "them", "their", "theirs", "themselves", "who", "whom", "whose", "which", "what", "where", "when",
            "why", "how", "there", "here",
            "am", "is", "are", "was", "were", "be", "been", "being", "do", "does", "did", "done", "doing", "have",
            "has", "had", "having", "will", "would", "shall", "should", "can", "could", "may", "might", "must",
            "ought", "isn't", "aren't", "wasn't", "weren't", "don't", "doesn't", "didn't", "haven't", "hasn't",
            "hadn't", "won't", "wouldn't", "can't", "couldn't", "shouldn't", "mustn't", "i'm", "it's", "i've",
            "i'll", "i'd", "you're", "we're", "they're", "that's", "there's",
            "and", "or", "but", "nor", "so", "yet", "for", "because", "although", "though", "while", "if", "unless",
            "than", "as", "since", "until", "till", "whether", "also", "too", "very", "just", "only", "not", "then",
            "of", "in", "on", "at", "to", "from", "by", "with", "without", "about", "above", "below", "under",
            "over", "into", "onto", "out", "up", "down", "off", "through", "during", "before", "after", "between",
            "among", "around", "against", "near", "behind", "across", "along", "within", "upon", "per", "via");

    private FunctionWords() {
    }

    public static boolean contains(String word) {
        return word != null && WORDS.contains(word.toLowerCase(Locale.ROOT).replace('’', '\''));
    }
}
