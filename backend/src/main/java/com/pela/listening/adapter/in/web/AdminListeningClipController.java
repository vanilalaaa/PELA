package com.pela.listening.adapter.in.web;

import com.pela.listening.application.port.in.ListeningClipAdminUseCase;
import com.pela.listening.application.port.in.ListeningClipAdminUseCase.AudioPreview;
import com.pela.listening.application.port.in.ListeningClipAdminUseCase.AudioRequest;
import com.pela.listening.application.port.in.ListeningClipAdminUseCase.ClipDetail;
import com.pela.listening.application.port.in.ListeningClipAdminUseCase.QuestionEdit;
import com.pela.listening.application.port.in.ListeningClipAdminUseCase.UpdateCommand;
import com.pela.listening.application.port.in.ListeningDrafts.LineDraft;
import com.pela.shared.adapter.in.web.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-19: Admin xem / sửa / retire bài nghe đã lưu (quyền Admin kiểm ở AdminAccessInterceptor cho mọi /v1/admin/**).
 */
@RestController
@RequestMapping("/v1/admin/listening/clips")
public class AdminListeningClipController {

    private final ListeningClipAdminUseCase clips;

    public AdminListeningClipController(ListeningClipAdminUseCase clips) {
        this.clips = clips;
    }

    @GetMapping("/{clipId}")
    public ClipDetail detail(@PathVariable UUID clipId) {
        return clips.detail(clipId);
    }

    /** Sinh audio nghe thử cho lời thoại + giọng mới; trả audioToken để gửi kèm bản sửa. */
    @PostMapping("/{clipId}/tts")
    public AudioPreview previewAudio(@CurrentUserId UUID adminId, @PathVariable UUID clipId,
            @Valid @RequestBody AudioPreviewRequest request) {
        return clips.previewAudio(adminId, clipId, new AudioRequest(request.voices(), request.lines()));
    }

    /** Sửa lời thoại / giọng cần audioToken khớp (409 AUDIO_REGENERATION_REQUIRED nếu thiếu, BR-37). */
    @PatchMapping("/{clipId}")
    public ClipDetail update(@CurrentUserId UUID adminId, @PathVariable UUID clipId,
            @RequestBody UpdateRequest request) {
        return clips.update(adminId, clipId, new UpdateCommand(request.title(), request.voices(), request.lines(),
                request.questions(), request.audioToken()));
    }

    /** Ẩn khỏi learner; không xoá bài, câu hỏi hay lịch sử làm bài (BR-34). */
    @PostMapping("/{clipId}/retire")
    public ClipDetail retire(@CurrentUserId UUID adminId, @PathVariable UUID clipId) {
        return clips.retire(adminId, clipId);
    }

    public record AudioPreviewRequest(@NotNull Map<String, String> voices, @NotEmpty List<LineDraft> lines) {
    }

    public record UpdateRequest(String title, Map<String, String> voices, List<LineDraft> lines,
            List<QuestionEdit> questions, String audioToken) {
    }
}
