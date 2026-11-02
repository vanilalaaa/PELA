package com.pela.rag.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pela.rag.domain.GeneratedPrompt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Output JSON của prompt rag_question_generation v2 (khớp response_schema trong V11). LlmPort parse vào record này và
 * chạy Bean Validation; không khớp → INVALID_JSON, không dùng output.
 */
public record GeneratedPromptResponse(
        @JsonProperty("prompt_text") @NotBlank String promptText,
        @JsonProperty("task_type") @NotBlank String taskType,
        @JsonProperty("min_words") @NotNull Integer minWords,
        @JsonProperty("max_words") @NotNull Integer maxWords) {

    GeneratedPrompt toDomain() {
        return new GeneratedPrompt(promptText, taskType, minWords, maxWords);
    }
}
