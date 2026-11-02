package com.pela.writing.application.port.in;

import java.util.UUID;

/** UC-10 bước 3–4: nộp bài Writing để chấm bất đồng bộ (Writing.Submit, BR-29). */
public interface SubmitWritingUseCase {

    /**
     * Idempotent theo {@code submissionId} do client sinh: gửi lại trả trạng thái hiện có. Hết quota → lưu nháp và
     * ném TooManyRequestsException (WRITING_QUOTA_EXCEEDED).
     */
    SubmitResult submit(UUID userId, SubmitCommand command);

    /** @param promptSource bank | ai_generated | freeform */
    record SubmitCommand(UUID submissionId, String promptSource, UUID promptQuestionId, String text) {

        @Override
        public String toString() {
            return "SubmitCommand[" + submissionId + ", " + promptSource + "]";
        }
    }

    record SubmitResult(UUID submissionId, String status) {
    }
}
