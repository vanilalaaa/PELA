package com.pela.writing.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Output JSON của prompt writing_grading v3 (khớp response_schema trong V8). LlmPort parse vào record này và chạy
 * Bean Validation; không khớp → INVALID_JSON, không dùng output.
 */
public record GradingResponse(
        @NotNull @Valid Criteria criteria,
        @NotNull List<@Valid @NotNull Issue> issues,
        @NotNull @Size(max = 10) List<@NotNull String> rewrites,
        @JsonProperty("vocab_candidates") @NotNull @Size(max = 10) List<@NotNull VocabCandidate> vocabCandidates,
        @NotBlank String comment) {

    public record Criteria(
            @JsonProperty("task_achievement") @NotNull @Min(0) @Max(25) Integer taskAchievement,
            @NotNull @Min(0) @Max(25) Integer coherence,
            @NotNull @Min(0) @Max(25) Integer vocabulary,
            @NotNull @Min(0) @Max(25) Integer grammar) {
    }

    public record Issue(
            @JsonProperty("sentence_index") @NotNull @Min(0) Integer sentenceIndex,
            @NotBlank String original,
            @NotNull String suggestion,
            @NotNull @Pattern(regexp = "grammar|spelling|word_choice|repetition|punctuation|style") String category,
            @NotNull @Pattern(regexp = "error|suggestion") String severity,
            @JsonProperty("explanation_vi") @NotBlank String explanationVi) {
    }

    /**
     * Không validate từng trường ở đây: ứng viên thiếu/sai (vd meaning_vi rỗng, cefr_level lạ) chỉ bị bỏ riêng ở
     * WorthLearningWords, không làm hỏng cả lần chấm.
     */
    public record VocabCandidate(
            @JsonProperty("learner_form") String learnerForm,
            String word,
            String reason,
            @JsonProperty("meaning_vi") String meaningVi,
            @JsonProperty("cefr_level") String cefrLevel) {
    }
}
