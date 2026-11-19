package com.pela.listening.adapter.in.web;

import com.pela.listening.application.port.in.ListeningPracticeUseCase;
import com.pela.listening.application.port.in.ListeningPracticeUseCase.Answer;
import com.pela.listening.application.port.in.ListeningPracticeUseCase.ClipItem;
import com.pela.listening.application.port.in.ListeningPracticeUseCase.ClipView;
import com.pela.listening.application.port.in.ListeningPracticeUseCase.SessionResult;
import com.pela.listening.application.port.in.ListeningPracticeUseCase.SubmitCommand;
import com.pela.listening.application.port.in.ListeningPracticeUseCase.TopicItem;
import com.pela.shared.adapter.in.web.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-11 Practice Listening. Chỉ map request ↔ use case. */
@RestController
@RequestMapping("/v1/practice/listening")
public class ListeningController {

    private final ListeningPracticeUseCase practice;

    public ListeningController(ListeningPracticeUseCase practice) {
        this.practice = practice;
    }

    @GetMapping("/topics")
    public List<TopicItem> topics(@RequestParam String level) {
        return practice.topics(level);
    }

    /** Sắp theo order_index do Admin đặt, không gợi ý (Listening.Select.Source). */
    @GetMapping("/clips")
    public List<ClipItem> clips(@CurrentUserId UUID userId, @RequestParam String topic, @RequestParam String level) {
        return practice.clips(userId, topic, level);
    }

    /** URL audio ký tạm + câu hỏi; không có đáp án, giải thích, transcript. */
    @GetMapping("/clips/{clipId}")
    public ClipView clip(@CurrentUserId UUID userId, @PathVariable UUID clipId) {
        return practice.clip(userId, clipId);
    }

    /** Nộp đáp án thô theo lô; server chấm (BR-23). Gửi lại cùng sessionId trả lại kết quả cũ. */
    @PostMapping("/sessions")
    public SessionResult submit(@CurrentUserId UUID userId, @Valid @RequestBody SubmitRequest request) {
        return practice.submit(userId, new SubmitCommand(request.sessionId(), request.clipId(), request.startedAt(),
                request.answers().stream().map(a -> new Answer(a.questionId(), a.selectedAnswer())).toList()));
    }

    /** @param sessionId UUID do client sinh, để gửi lại an toàn */
    public record SubmitRequest(
            @NotNull UUID sessionId,
            @NotNull UUID clipId,
            Instant startedAt,
            @NotNull @Size(min = 1, max = 50) List<@Valid @NotNull AnswerRequest> answers) {
    }

    public record AnswerRequest(@NotNull UUID questionId, @NotNull @Size(max = 1) String selectedAnswer) {
    }
}
