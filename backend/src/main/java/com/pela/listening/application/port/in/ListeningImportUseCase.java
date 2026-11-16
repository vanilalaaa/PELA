package com.pela.listening.application.port.in;

import com.pela.listening.application.port.in.ListeningDrafts.ClipDraft;
import com.pela.listening.application.port.in.ListeningDrafts.GeneratedAudio;
import com.pela.listening.application.port.in.ListeningDrafts.Issue;
import com.pela.listening.application.port.in.ListeningDrafts.SavedAudio;
import java.util.List;
import java.util.UUID;

/**
 * Phần Listening của luồng nhập file CSV (UC-19, Admin.QuestionBank.ImportListening/Tts/Save, BR-37). Module admin
 * giữ import job, Storage và nhật ký; module này đọc/kiểm CSV, sinh audio và tạo listening_clip + question_bank.
 */
public interface ListeningImportUseCase {

    /** @param headerValid false = sai cột / file hỏng: không có bài nghe nào */
    record ParsedImport(List<ClipDraft> clips, List<Issue> issues, boolean headerValid) {
    }

    /** Đọc CSV theo mẫu và kiểm BR-37 với giới hạn ở system_setting. */
    ParsedImport parseCsv(String csv);

    /** Kiểm lại toàn bộ bài nghe (sau khi Admin sửa preview). */
    List<Issue> validate(List<ClipDraft> clips);

    /** Dấu vân tay lời thoại + giọng hiện tại của bài nghe; audio chỉ dùng được khi khớp (BR-37). */
    String audioFingerprint(ClipDraft clip);

    /**
     * Sinh audio TTS cho một bài nghe hợp lệ, giọng đọc theo cấp CEFR (AS-16). Bài còn lỗi hoặc người nói chưa có giọng
     * → ValidationFailedException; TTS lỗi → AiCallException.
     */
    GeneratedAudio synthesize(ClipDraft clip);

    /**
     * Tạo listening_clip + question_bank (question_purpose = listening) với order_index tiếp theo trong topic + cấp.
     * Gọi bên trong transaction của người điều phối; bài nghe phải hợp lệ.
     *
     * @return clip_id
     */
    UUID createClip(UUID clipId, UUID adminId, UUID importJobId, ClipDraft clip, SavedAudio audio);
}
