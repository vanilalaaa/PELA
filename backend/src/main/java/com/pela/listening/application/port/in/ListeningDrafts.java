package com.pela.listening.application.port.in;

import java.util.List;
import java.util.Map;

/** Dữ liệu bài nghe đang nhập / đang sửa (preview của import job, BR-34), dùng chung cho các use case Admin. */
public final class ListeningDrafts {

    private ListeningDrafts() {
    }

    /** @param row dòng CSV đầu tiên của bài nghe; null khi không còn dòng gốc */
    public record ClipDraft(String clipCode, Integer row, String title, String topic, String cefrLevel,
            Map<String, String> voices, List<LineDraft> lines, List<QuestionDraft> questions) {

        public ClipDraft {
            voices = voices == null ? Map.of() : voices;
            lines = lines == null ? List.of() : lines;
            questions = questions == null ? List.of() : questions;
        }
    }

    public record LineDraft(Integer row, String speaker, String en, String vi) {
    }

    /** @param answer chữ cái A–D của lựa chọn đúng */
    public record QuestionDraft(Integer row, String type, String text, List<String> options, String answer,
            String explanation) {

        public QuestionDraft {
            options = options == null ? List.of() : options;
        }
    }

    /** Lỗi theo dòng CSV và cột (UC-19 E3); {@code clipIndex} null = lỗi cả file. */
    public record Issue(Integer clipIndex, Integer row, String column, String code, String message) {
    }

    /** Audio TTS vừa sinh, chưa lưu; {@code fingerprint} là dấu vân tay lời thoại + giọng lúc sinh (BR-37). */
    public record GeneratedAudio(byte[] wav, int durationSec, String ttsModel, String format, String fingerprint) {

        @Override
        public String toString() {
            return "GeneratedAudio[bytes=" + wav.length + ", durationSec=" + durationSec + ", model=" + ttsModel + "]";
        }
    }

    /** Audio đã nằm ở bucket listening-audio. */
    public record SavedAudio(String path, int durationSec, String ttsModel, String format) {
    }
}
