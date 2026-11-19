package com.pela.listening.application.port.in;

import com.pela.listening.application.port.in.ListeningDrafts.LineDraft;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC-19: Admin xem, sửa, retire bài nghe đã lưu. Sửa lời thoại hoặc giọng thì phải sinh lại audio và nghe thử trước
 * (BR-37); Retire chỉ ẩn khỏi learner, không xoá lịch sử (BR-34). Mọi thay đổi ghi nhật ký (BR-35).
 */
public interface ListeningClipAdminUseCase {

    record QuestionDetail(UUID questionId, String type, String text, List<String> options, String answer,
            String explanation, String status) {
    }

    record ClipDetail(UUID clipId, String title, String topic, String cefrLevel, int orderIndex, String status,
            Integer durationSec, String ttsModel, Map<String, String> voices, List<LineDraft> lines,
            List<QuestionDetail> questions, String audioUrl, String audioFingerprint, Instant retiredAt) {
    }

    /** Audio nghe thử cho lời thoại + giọng mới; {@code audioToken} dùng khi gửi bản sửa. */
    record AudioPreview(String audioToken, String fingerprint, String audioUrl, int durationSec) {
    }

    record AudioRequest(Map<String, String> voices, List<LineDraft> lines) {
    }

    record QuestionEdit(UUID questionId, String text, List<String> options, String answer, String explanation) {
    }

    /**
     * Trường null = giữ nguyên. Đổi {@code lines}/{@code voices} thì bắt buộc có {@code audioToken} của bản nghe thử
     * sinh từ đúng lời thoại + giọng mới.
     */
    record UpdateCommand(String title, Map<String, String> voices, List<LineDraft> lines, List<QuestionEdit> questions,
            String audioToken) {
    }

    ClipDetail detail(UUID clipId);

    AudioPreview previewAudio(UUID adminId, UUID clipId, AudioRequest request);

    ClipDetail update(UUID adminId, UUID clipId, UpdateCommand command);

    ClipDetail retire(UUID adminId, UUID clipId);
}
