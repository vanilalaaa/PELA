package com.pela.admin.application.port.in;

import com.pela.listening.application.port.in.ListeningDrafts.ClipDraft;
import com.pela.listening.application.port.in.ListeningDrafts.Issue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC-19 nhập Listening từ CSV (import_job.kind = listening_csv): tải file → xử lý nền → preview_ready → Admin sửa
 * preview, sinh audio từng bài, nghe thử → lưu (BR-14, BR-34, BR-35, BR-37).
 */
public interface ListeningImportJobUseCase {

    String KIND = "listening_csv";

    record UploadCommand(String kind, String filename, String contentType, byte[] content) {

        @Override
        public String toString() {
            return "UploadCommand[kind=" + kind + ", bytes=" + (content == null ? 0 : content.length) + "]";
        }
    }

    /**
     * @param fingerprint dấu vân tay lời thoại + giọng lúc sinh audio
     * @param current     audio khớp lời thoại + giọng hiện tại của bài (lưu được)
     */
    record ClipAudioView(String fingerprint, int durationSec, String ttsModel, String format, boolean current) {
    }

    record PreviewClip(ClipDraft clip, ClipAudioView audio, UUID savedClipId) {
    }

    record JobView(UUID jobId, String kind, String status, int attempts, String error, Instant createdAt,
            Instant finishedAt, List<PreviewClip> clips, List<Issue> warnings) {
    }

    record AudioPreview(int clipIndex, String audioUrl, int durationSec, String fingerprint, int attempts) {
    }

    record SaveResult(UUID jobId, List<UUID> clipIds) {
    }

    /** Kiểm loại + dung lượng file, lưu vào bucket imports, tạo job queued và kích hoạt xử lý nền. */
    JobView upload(UUID adminId, UploadCommand command);

    JobView get(UUID jobId);

    /** Xử lý nền: đọc + kiểm CSV → preview_ready (hoặc failed khi sai cột / không đọc được). */
    void process(UUID jobId);

    /** Job queued cũ (vd server khởi động lại giữa chừng); trả số job đã xử lý. */
    int processQueued(int limit);

    /** Admin sửa preview: thay toàn bộ danh sách bài, kiểm lại BR-37; audio cũ giữ nhưng hết hợp lệ nếu lời/giọng đổi. */
    JobView updatePreview(UUID adminId, UUID jobId, List<ClipDraft> clips);

    /** Sinh audio TTS cho một bài, lưu WAV tạm ở bucket imports, attempts + 1. */
    AudioPreview generateAudio(UUID adminId, UUID jobId, int clipIndex);

    /** Lưu mọi bài khi không còn lỗi và audio của mọi bài khớp lời thoại + giọng hiện tại. */
    SaveResult save(UUID adminId, UUID jobId);
}
