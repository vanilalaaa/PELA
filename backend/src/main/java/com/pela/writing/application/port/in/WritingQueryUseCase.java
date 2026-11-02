package com.pela.writing.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Xem kết quả chấm (Writing.Grade.Output) và lịch sử bài viết (Writing.History.List). Chỉ bài của chính learner. */
public interface WritingQueryUseCase {

    SubmissionView submission(UUID userId, UUID submissionId);

    History history(UUID userId, int limit);

    record SubmissionView(UUID submissionId, String status, String promptSource, String promptText,
            String submittedText, int wordCount, Integer score, Integer starsEarned, Map<String, Integer> criteria,
            String comment, List<String> rewrites, List<IssueView> issues, List<VocabSuggestionView> vocabSuggestions,
            Instant submittedAt, Instant gradedAt, Instant nextRetryAt) {
    }

    record IssueView(UUID issueId, int sentenceIndex, int startOffset, int endOffset, String original, String suggestion,
            String category, String severity, String explanationVi) {
    }

    record VocabSuggestionView(UUID suggestionId, String learnerForm, String word, String reason, String meaningVi,
            String cefrLevel, String status) {
    }

    record History(List<HistoryItem> items, List<ScorePoint> scoreTrend, List<CategoryCount> topIssueCategories) {
    }

    record HistoryItem(UUID submissionId, String status, String promptSource, String promptText, int wordCount,
            Integer score, Integer starsEarned, Instant submittedAt, Instant gradedAt) {
    }

    record ScorePoint(UUID submissionId, Instant gradedAt, int score) {
    }

    record CategoryCount(String category, long count) {
    }
}
