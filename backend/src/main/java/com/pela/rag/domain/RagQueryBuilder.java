package com.pela.rag.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RAG.Embed.Input: hàm DUY NHẤT dựng câu truy vấn đem đi embed, từ topic + CEFR level + sở thích của learner.
 * UC-24 và các bài đo (ngưỡng, độ trễ) đều phải gọi hàm này, để số đo phản ánh đúng truy vấn thật của hệ thống.
 */
public final class RagQueryBuilder {

    /** Sở thích chỉ để gợi ý ngữ cảnh; giới hạn số lượng để truy vấn ngắn và ổn định. */
    static final int MAX_INTERESTS = 5;

    private RagQueryBuilder() {
    }

    /**
     * @param interests sở thích learner (learner_profile.interests); null hoặc rỗng thì bỏ qua. Bỏ phần tử trống và
     *                  trùng (không phân biệt hoa thường), giữ thứ tự, tối đa {@value #MAX_INTERESTS}.
     */
    public static String build(String topic, String cefrLevel, List<String> interests) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (cefrLevel == null || cefrLevel.isBlank()) {
            throw new IllegalArgumentException("cefrLevel is required");
        }
        StringBuilder query = new StringBuilder("Writing prompt. Topic: ").append(topic.strip())
                .append(". CEFR level: ").append(cefrLevel.strip()).append('.');
        List<String> cleaned = clean(interests);
        if (!cleaned.isEmpty()) {
            query.append(" Learner interests: ").append(String.join(", ", cleaned)).append('.');
        }
        return query.toString();
    }

    /** Chuỗi sở thích đưa vào prompt LLM; "none" khi không có (prompt không để trống biến). */
    public static String interestsText(List<String> interests) {
        List<String> cleaned = clean(interests);
        return cleaned.isEmpty() ? "none" : String.join(", ", cleaned);
    }

    private static List<String> clean(List<String> interests) {
        if (interests == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String interest : interests) {
            if (interest == null || interest.isBlank()) {
                continue;
            }
            String value = interest.strip();
            if (seen.add(value.toLowerCase(Locale.ROOT)) && result.size() < MAX_INTERESTS) {
                result.add(value);
            }
        }
        return result;
    }
}
