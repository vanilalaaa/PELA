package com.pela.writing.application.port.out;

import com.pela.writing.application.port.in.WritingQueryUseCase.History;
import com.pela.writing.application.port.in.WritingQueryUseCase.SubmissionView;
import com.pela.writing.domain.WorthLearningWords;
import com.pela.writing.domain.WritingGrading.CriterionScores;
import com.pela.writing.domain.WritingGrading.LocatedIssue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bảng writing_submission, writing_issue, writing_vocab_suggestion. */
public interface WritingSubmissionPort {

    Optional<Existing> find(UUID submissionId);

    /** Thêm bài mới; false nếu submissionId đã tồn tại (gửi lại / gửi trùng đồng thời). */
    boolean insert(NewSubmission submission);

    /** Cập nhật bản nháp đã có thành bài nộp/nháp mới (cùng chủ, đang ở trạng thái draft). */
    boolean updateDraft(NewSubmission submission);

    /**
     * Bài đang giữ lượt chấm (BR-29) trong số bài nộp ([submitted_at]) thuộc khoảng [from, to): đã chấm (graded) và
     * đang chờ chấm (pending, retry_queued). Bài failed trả lượt nên không tính.
     */
    QuotaUsage quotaUsage(UUID userId, Instant from, Instant to);

    /** Id bài cần chấm: pending hoặc retry_queued có next_retry_at null/≤ now. */
    List<UUID> dueForGrading(Instant now, int limit);

    /**
     * Nhận bài để chấm một cách nguyên tử: tăng grading_attempts và đặt next_retry_at = leaseUntil để worker khác
     * không nhận trùng. Rỗng nếu bài không còn cần chấm hoặc chưa tới hạn.
     */
    Optional<Claimed> claim(UUID submissionId, Instant now, Instant leaseUntil);

    void markRetryQueued(UUID submissionId, Instant nextRetryAt);

    void markFailed(UUID submissionId);

    /**
     * Lưu kết quả chấm trong một transaction: bài → graded, các issue và từ đáng học. false nếu bài đã được chấm ở
     * nơi khác (không lưu gì).
     */
    boolean saveGraded(GradedSubmission graded);

    void markSuggestionAdded(UUID suggestionId, String status, UUID vocabId, UUID reviewCardId);

    Optional<SuggestionRecord> suggestion(UUID suggestionId);

    Optional<SubmissionView> view(UUID userId, UUID submissionId);

    History history(UUID userId, int limit);

    record Existing(UUID submissionId, UUID userId, String status) {
    }

    record NewSubmission(UUID submissionId, UUID userId, String promptSource, UUID promptQuestionId, String promptText,
            String text, int wordCount, String status, Instant submittedAt) {

        @Override
        public String toString() {
            return "NewSubmission[" + submissionId + ", " + status + "]";
        }
    }

    record QuotaUsage(int gradedToday, int inFlight) {

        public int used() {
            return gradedToday + inFlight;
        }
    }

    /** Bài đã nhận để chấm. {@code attempts} đã gồm lượt hiện tại. */
    record Claimed(UUID submissionId, UUID userId, String text, String promptText, UUID promptQuestionId, int wordCount,
            int attempts) {

        @Override
        public String toString() {
            return "Claimed[" + submissionId + ", attempt " + attempts + "]";
        }
    }

    record GradedSubmission(UUID submissionId, int score, CriterionScores criteria, int stars,
            Map<String, Object> aiFeedback, int promptVersion, String model, Instant gradedAt,
            List<IssueRecord> issues, List<SuggestionToSave> suggestions) {
    }

    record IssueRecord(UUID issueId, LocatedIssue issue) {
    }

    /** {@code status} = auto_added (đã có trong kho, BR-9) hoặc suggested. */
    record SuggestionToSave(UUID suggestionId, UUID userId, WorthLearningWords.Selected word, String status,
            UUID vocabId, UUID reviewCardId) {
    }

    record SuggestionRecord(UUID suggestionId, UUID submissionId, UUID userId, String word, String meaningVi,
            String cefrLevel, String status, UUID vocabId, UUID reviewCardId) {
    }
}
