package com.pela.writing.adapter.in.web;

import com.pela.shared.adapter.in.web.CurrentUserId;
import com.pela.writing.application.port.in.AddSuggestedWordUseCase;
import com.pela.writing.application.port.in.AddSuggestedWordUseCase.AddedWord;
import com.pela.writing.application.port.in.SubmitWritingUseCase;
import com.pela.writing.application.port.in.SubmitWritingUseCase.SubmitCommand;
import com.pela.writing.application.port.in.SubmitWritingUseCase.SubmitResult;
import com.pela.writing.application.port.in.WritingQueryUseCase;
import com.pela.writing.application.port.in.WritingQueryUseCase.History;
import com.pela.writing.application.port.in.WritingQueryUseCase.SubmissionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-10 / UC-23: nộp bài, xem kết quả chấm, lịch sử, thêm từ đáng học. Chỉ map request ↔ use case. */
@RestController
@RequestMapping("/v1/practice/writing")
public class WritingController {

    private final SubmitWritingUseCase submit;
    private final WritingQueryUseCase query;
    private final AddSuggestedWordUseCase addWord;

    public WritingController(SubmitWritingUseCase submit, WritingQueryUseCase query, AddSuggestedWordUseCase addWord) {
        this.submit = submit;
        this.query = query;
        this.addWord = addWord;
    }

    /** 202: đã nhận, chấm bất đồng bộ. Hết lượt chấm hôm nay → 429, bài được giữ dưới dạng nháp (BR-29). */
    @PostMapping("/submissions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SubmitResult submit(@CurrentUserId UUID userId, @Valid @RequestBody SubmitRequest request) {
        return submit.submit(userId, new SubmitCommand(request.submissionId(), request.promptSource(),
                request.promptQuestionId(), request.text()));
    }

    @GetMapping("/submissions/{submissionId}")
    public SubmissionView submission(@CurrentUserId UUID userId, @PathVariable UUID submissionId) {
        return query.submission(userId, submissionId);
    }

    @GetMapping("/submissions")
    public History history(@CurrentUserId UUID userId, @RequestParam(defaultValue = "20") int limit) {
        return query.history(userId, limit);
    }

    @PostMapping("/vocab-suggestions/{suggestionId}/add")
    public AddedWord addWord(@CurrentUserId UUID userId, @PathVariable UUID suggestionId) {
        return addWord.add(userId, suggestionId);
    }

    /** @param submissionId UUID do client sinh, để gửi lại an toàn */
    public record SubmitRequest(
            @NotNull UUID submissionId,
            @NotNull @Pattern(regexp = "bank|ai_generated|freeform") String promptSource,
            UUID promptQuestionId,
            @NotBlank @Size(max = 5000) String text) {

        @Override
        public String toString() {
            return "SubmitRequest[" + submissionId + "]";
        }
    }
}
