package com.pela.listening.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Luật nội dung bài nghe BR-37 / AS-16: có tiêu đề, topic, cấp CEFR hợp lệ; ít nhất 1 câu thoại và 1 câu hỏi; tối đa
 * {@code maxSpeakers} người nói và {@code maxChars} ký tự lời thoại; mỗi câu hỏi có loại hợp lệ, 4 lựa chọn khác nhau,
 * đúng 1 đáp án A–D và có giải thích. Mỗi lỗi kèm dòng CSV và tên cột.
 */
public final class ListeningClipValidator {

    public static final Set<String> CEFR_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    /** Listening.Answer.Types: chọn từ/cụm nghe được, điền từ còn thiếu, trả lời câu hỏi về đoạn hội thoại. */
    public static final Set<String> QUESTION_TYPES = Set.of("choose_heard", "fill_blank", "dialogue_question");
    public static final List<String> ANSWERS = List.of("A", "B", "C", "D");
    private static final List<String> OPTION_COLUMNS = List.of("option_a", "option_b", "option_c", "option_d");
    /** Độ dài cột trong listening_clip / question_bank. */
    private static final int MAX_TITLE = 150;
    private static final int MAX_TOPIC = 100;

    private ListeningClipValidator() {
    }

    public record Limits(int maxSpeakers, int maxChars) {
    }

    public static List<ImportIssue> validate(int clipIndex, ClipSpec clip, Limits limits) {
        List<ImportIssue> issues = new ArrayList<>();
        Integer row = clip.row();
        required(issues, clipIndex, row, "title", clip.title());
        required(issues, clipIndex, row, "topic", clip.topic());
        if (clip.title() != null && clip.title().strip().length() > MAX_TITLE) {
            issues.add(new ImportIssue(clipIndex, row, "title", "TOO_LONG", "title exceeds " + MAX_TITLE + " characters"));
        }
        if (clip.topic() != null && clip.topic().strip().length() > MAX_TOPIC) {
            issues.add(new ImportIssue(clipIndex, row, "topic", "TOO_LONG", "topic exceeds " + MAX_TOPIC + " characters"));
        }
        if (clip.cefrLevel() == null || !CEFR_LEVELS.contains(clip.cefrLevel().strip().toUpperCase(Locale.ROOT))) {
            issues.add(new ImportIssue(clipIndex, row, "cefr_level", "INVALID_CEFR_LEVEL",
                    "cefr_level must be one of A1, A2, B1, B2, C1, C2"));
        }
        if (clip.lines().isEmpty()) {
            issues.add(new ImportIssue(clipIndex, row, "row_type", "NO_LINES", "Clip has no transcript line"));
        }
        if (clip.questions().isEmpty()) {
            issues.add(new ImportIssue(clipIndex, row, "row_type", "NO_QUESTIONS", "Clip has no question"));
        }

        Set<String> speakers = new HashSet<>();
        for (ClipSpec.Line line : clip.lines()) {
            required(issues, clipIndex, line.row(), "speaker", line.speaker());
            required(issues, clipIndex, line.row(), "text_en", line.en());
            required(issues, clipIndex, line.row(), "text_vi", line.vi());
            if (!isBlank(line.speaker()) && speakers.add(line.speaker().strip()) && speakers.size() > limits.maxSpeakers()) {
                issues.add(new ImportIssue(clipIndex, line.row(), "speaker", "TOO_MANY_SPEAKERS",
                        "A clip has at most " + limits.maxSpeakers() + " speakers"));
            }
        }
        int chars = clip.transcriptChars();
        if (chars > limits.maxChars()) {
            issues.add(new ImportIssue(clipIndex, row, "text_en", "TRANSCRIPT_TOO_LONG",
                    "Transcript has " + chars + " characters, the limit is " + limits.maxChars()));
        }

        for (ClipSpec.Question question : clip.questions()) {
            validateQuestion(issues, clipIndex, question);
        }
        return issues;
    }

    private static void validateQuestion(List<ImportIssue> issues, int clipIndex, ClipSpec.Question question) {
        Integer row = question.row();
        if (question.type() == null || !QUESTION_TYPES.contains(question.type().strip().toLowerCase(Locale.ROOT))) {
            issues.add(new ImportIssue(clipIndex, row, "question_type", "INVALID_QUESTION_TYPE",
                    "question_type must be one of choose_heard, fill_blank, dialogue_question"));
        }
        required(issues, clipIndex, row, "text_en", question.text());
        if (question.options().size() != OPTION_COLUMNS.size()) {
            issues.add(new ImportIssue(clipIndex, row, "option_a", "OPTION_COUNT", "A question has exactly 4 options"));
        } else {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < OPTION_COLUMNS.size(); i++) {
                String option = question.options().get(i);
                if (isBlank(option)) {
                    required(issues, clipIndex, row, OPTION_COLUMNS.get(i), option);
                } else if (!seen.add(option.strip().toLowerCase(Locale.ROOT))) {
                    issues.add(new ImportIssue(clipIndex, row, OPTION_COLUMNS.get(i), "DUPLICATE_OPTION",
                            "Options must be distinct"));
                }
            }
        }
        if (question.answer() == null || !ANSWERS.contains(question.answer().strip().toUpperCase(Locale.ROOT))) {
            issues.add(new ImportIssue(clipIndex, row, "answer", "INVALID_ANSWER",
                    "answer must be exactly one of A, B, C, D"));
        }
        required(issues, clipIndex, row, "explanation", question.explanation());
    }

    private static void required(List<ImportIssue> issues, int clipIndex, Integer row, String column, String value) {
        if (isBlank(value)) {
            issues.add(new ImportIssue(clipIndex, row, column, "REQUIRED", column + " is required"));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
