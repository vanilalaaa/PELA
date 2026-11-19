package com.pela.listening.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Một bài nghe trước khi lưu (BR-37): tiêu đề, topic, cấp CEFR, giọng theo người nói, lời thoại và câu hỏi.
 * {@code row} của từng phần là số dòng CSV để báo lỗi; bản do Admin sửa trên preview có thể không có (null).
 */
public record ClipSpec(String clipCode, Integer row, String title, String topic, String cefrLevel,
        Map<String, String> voices, List<Line> lines, List<Question> questions) {

    public ClipSpec {
        voices = voices == null ? Map.of() : Map.copyOf(voices);
        lines = lines == null ? List.of() : List.copyOf(lines);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    /** Lời thoại: người nói, câu tiếng Anh (được đọc), bản dịch tiếng Việt. */
    public record Line(Integer row, String speaker, String en, String vi) {
    }

    /** Câu hỏi: 4 lựa chọn A–D, đáp án là một chữ cái A–D, có giải thích. */
    public record Question(Integer row, String type, String text, List<String> options, String answer,
            String explanation) {

        public Question {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** Người nói theo thứ tự xuất hiện. */
    public Set<String> speakers() {
        Set<String> speakers = new LinkedHashSet<>();
        for (Line line : lines) {
            if (line.speaker() != null && !line.speaker().isBlank()) {
                speakers.add(line.speaker().strip());
            }
        }
        return speakers;
    }

    /** Tổng số ký tự lời thoại tiếng Anh (phần được đọc thành audio, AS-16). */
    public int transcriptChars() {
        int total = 0;
        for (Line line : lines) {
            total += line.en() == null ? 0 : line.en().strip().length();
        }
        return total;
    }
}
