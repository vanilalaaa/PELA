package com.pela.admin.application.port.out;

import com.pela.listening.application.port.in.ListeningDrafts.ClipDraft;
import com.pela.listening.application.port.in.ListeningDrafts.Issue;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bảng import_job. Preview + warnings lưu ở cột jsonb; Admin sửa trực tiếp trên preview (BR-34). */
public interface ImportJobPort {

    record SourceFile(String path, int bytes, String filename) {
    }

    /** Audio đã sinh cho một bài của preview (WAV tạm ở bucket imports). */
    record ClipAudio(String path, String fingerprint, int durationSec, String ttsModel, String format) {
    }

    record StoredClip(ClipDraft clip, ClipAudio audio, UUID savedClipId) {
    }

    record StoredJob(UUID jobId, String kind, String status, SourceFile file, int attempts, String error,
            UUID createdBy, Instant createdAt, Instant finishedAt, List<StoredClip> clips, List<Issue> warnings) {
    }

    void insert(UUID jobId, String kind, SourceFile file, UUID createdBy, Instant createdAt);

    Optional<StoredJob> find(UUID jobId);

    /** queued → processing; false nếu job không còn queued (đã có worker khác nhận). */
    boolean claim(UUID jobId);

    List<UUID> queued(String kind, int limit);

    void previewReady(UUID jobId, List<StoredClip> clips, List<Issue> warnings, Instant at);

    void failed(UUID jobId, String error, List<Issue> warnings, Instant at);

    /** Thay danh sách bài + warnings; chỉ khi job đang preview_ready. */
    boolean replacePreview(UUID jobId, List<StoredClip> clips, List<Issue> warnings);

    /** Gắn audio cho bài thứ {@code clipIndex} và attempts + 1; trả attempts mới, rỗng nếu job không preview_ready. */
    Optional<Integer> setClipAudio(UUID jobId, int clipIndex, ClipAudio audio);

    /** preview_ready → saved, ghi clip_id đã tạo; false nếu job không còn preview_ready. */
    boolean markSaved(UUID jobId, List<StoredClip> clips, Instant at);
}
