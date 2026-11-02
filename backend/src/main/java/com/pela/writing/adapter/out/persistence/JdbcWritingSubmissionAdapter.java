package com.pela.writing.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.writing.application.port.in.WritingQueryUseCase.CategoryCount;
import com.pela.writing.application.port.in.WritingQueryUseCase.History;
import com.pela.writing.application.port.in.WritingQueryUseCase.HistoryItem;
import com.pela.writing.application.port.in.WritingQueryUseCase.IssueView;
import com.pela.writing.application.port.in.WritingQueryUseCase.ScorePoint;
import com.pela.writing.application.port.in.WritingQueryUseCase.SubmissionView;
import com.pela.writing.application.port.in.WritingQueryUseCase.VocabSuggestionView;
import com.pela.writing.application.port.out.WritingSubmissionPort;
import com.pela.writing.domain.WritingGrading.CriterionScores;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcWritingSubmissionAdapter implements WritingSubmissionPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcWritingSubmissionAdapter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<Existing> find(UUID submissionId) {
        return jdbc.query("SELECT submission_id, user_id, grading_status FROM writing_submission WHERE submission_id = ?",
                (rs, i) -> new Existing(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)),
                submissionId).stream().findFirst();
    }

    @Override
    public boolean insert(NewSubmission s) {
        return jdbc.update("""
                INSERT INTO writing_submission (submission_id, user_id, prompt_question_id, prompt_source, prompt_text,
                                                submitted_text, word_count, grading_status, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (submission_id) DO NOTHING
                """, s.submissionId(), s.userId(), s.promptQuestionId(), s.promptSource(), s.promptText(), s.text(),
                s.wordCount(), s.status(), timestamp(s.submittedAt())) > 0;
    }

    @Override
    public boolean updateDraft(NewSubmission s) {
        return jdbc.update("""
                UPDATE writing_submission
                SET prompt_question_id = ?, prompt_source = ?, prompt_text = ?, submitted_text = ?, word_count = ?,
                    grading_status = ?, submitted_at = ?
                WHERE submission_id = ? AND user_id = ? AND grading_status = 'draft'
                """, s.promptQuestionId(), s.promptSource(), s.promptText(), s.text(), s.wordCount(), s.status(),
                timestamp(s.submittedAt()), s.submissionId(), s.userId()) > 0;
    }

    @Override
    public QuotaUsage quotaUsage(UUID userId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE grading_status = 'graded'),
                       count(*) FILTER (WHERE grading_status IN ('pending', 'retry_queued'))
                FROM writing_submission
                WHERE user_id = ? AND submitted_at >= ? AND submitted_at < ?
                """, (rs, i) -> new QuotaUsage(rs.getInt(1), rs.getInt(2)), userId, timestamp(from), timestamp(to));
    }

    @Override
    public List<UUID> dueForGrading(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT submission_id FROM writing_submission
                WHERE grading_status IN ('pending', 'retry_queued') AND (next_retry_at IS NULL OR next_retry_at <= ?)
                ORDER BY submitted_at
                LIMIT ?
                """, UUID.class, timestamp(now), limit);
    }

    @Override
    public Optional<Claimed> claim(UUID submissionId, Instant now, Instant leaseUntil) {
        return jdbc.query("""
                        UPDATE writing_submission
                        SET grading_attempts = grading_attempts + 1, next_retry_at = ?
                        WHERE submission_id = ? AND grading_status IN ('pending', 'retry_queued')
                          AND (next_retry_at IS NULL OR next_retry_at <= ?)
                        RETURNING submission_id, user_id, submitted_text, prompt_text, prompt_question_id, word_count,
                                  grading_attempts
                        """,
                (rs, i) -> new Claimed(rs.getObject("submission_id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("submitted_text"), rs.getString("prompt_text"),
                        rs.getObject("prompt_question_id", UUID.class), rs.getInt("word_count"),
                        rs.getInt("grading_attempts")),
                timestamp(leaseUntil), submissionId, timestamp(now)).stream().findFirst();
    }

    @Override
    public void markRetryQueued(UUID submissionId, Instant nextRetryAt) {
        jdbc.update("UPDATE writing_submission SET grading_status = 'retry_queued', next_retry_at = ? WHERE submission_id = ?",
                timestamp(nextRetryAt), submissionId);
    }

    @Override
    public void markFailed(UUID submissionId) {
        jdbc.update("UPDATE writing_submission SET grading_status = 'failed', next_retry_at = NULL WHERE submission_id = ?",
                submissionId);
    }

    @Override
    public boolean saveGraded(GradedSubmission g) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            int updated = jdbc.update("""
                    UPDATE writing_submission
                    SET grading_status = 'graded', score = ?, criteria_scores = ?::jsonb, stars_earned = ?,
                        ai_feedback = ?::jsonb, prompt_version = ?, model = ?, graded_at = ?, next_retry_at = NULL
                    WHERE submission_id = ? AND grading_status IN ('pending', 'retry_queued')
                    """, g.score(), json(criteriaMap(g.criteria())), g.stars(), json(g.aiFeedback()), g.promptVersion(),
                    g.model(), timestamp(g.gradedAt()), g.submissionId());
            if (updated == 0) {
                return false; // Đã được chấm ở nơi khác
            }
            for (IssueRecord r : g.issues()) {
                var i = r.issue();
                jdbc.update("""
                        INSERT INTO writing_issue (issue_id, submission_id, sentence_index, start_offset, end_offset,
                                                   original, suggestion, category, severity, explanation_vi, detected_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'llm')
                        """, r.issueId(), g.submissionId(), i.sentenceIndex(), i.start(), i.end(), i.original(),
                        i.suggestion(), i.category().dbValue(), i.severity().dbValue(), i.explanationVi());
            }
            for (SuggestionToSave s : g.suggestions()) {
                var w = s.word();
                jdbc.update("""
                        INSERT INTO writing_vocab_suggestion (suggestion_id, submission_id, user_id, learner_form, word,
                                                              reason, meaning_vi, cefr_level, vocab_id, status, review_card_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (submission_id, word) DO NOTHING
                        """, s.suggestionId(), g.submissionId(), s.userId(), w.learnerForm(), w.word(),
                        w.reason().dbValue(), w.meaningVi(), w.cefrLevel() == null ? null : w.cefrLevel().name(),
                        s.vocabId(), s.status(), s.reviewCardId());
            }
            return true;
        }));
    }

    @Override
    public void markSuggestionAdded(UUID suggestionId, String status, UUID vocabId, UUID reviewCardId) {
        jdbc.update("UPDATE writing_vocab_suggestion SET status = ?, vocab_id = ?, review_card_id = ? WHERE suggestion_id = ?",
                status, vocabId, reviewCardId, suggestionId);
    }

    @Override
    public Optional<SuggestionRecord> suggestion(UUID suggestionId) {
        return jdbc.query("""
                        SELECT suggestion_id, submission_id, user_id, word, meaning_vi, cefr_level, status, vocab_id,
                               review_card_id
                        FROM writing_vocab_suggestion WHERE suggestion_id = ?
                        """,
                (rs, i) -> new SuggestionRecord(rs.getObject("suggestion_id", UUID.class),
                        rs.getObject("submission_id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("word"), rs.getString("meaning_vi"), rs.getString("cefr_level"),
                        rs.getString("status"), rs.getObject("vocab_id", UUID.class),
                        rs.getObject("review_card_id", UUID.class)),
                suggestionId).stream().findFirst();
    }

    @Override
    public Optional<SubmissionView> view(UUID userId, UUID submissionId) {
        return jdbc.query("""
                        SELECT submission_id, grading_status, prompt_source, prompt_text, submitted_text, word_count,
                               score, stars_earned, criteria_scores::text AS criteria, ai_feedback::text AS feedback,
                               submitted_at, graded_at, next_retry_at
                        FROM writing_submission WHERE submission_id = ? AND user_id = ?
                        """,
                (rs, i) -> toView(rs), submissionId, userId).stream().findFirst();
    }

    @Override
    public History history(UUID userId, int limit) {
        List<HistoryItem> items = jdbc.query("""
                        SELECT submission_id, grading_status, prompt_source, prompt_text, word_count, score, stars_earned,
                               submitted_at, graded_at
                        FROM writing_submission WHERE user_id = ?
                        ORDER BY coalesce(submitted_at, created_at) DESC
                        LIMIT ?
                        """,
                (rs, i) -> new HistoryItem(rs.getObject("submission_id", UUID.class), rs.getString("grading_status"),
                        rs.getString("prompt_source"), rs.getString("prompt_text"), rs.getInt("word_count"),
                        (Integer) rs.getObject("score"), (Integer) rs.getObject("stars_earned"),
                        instant(rs.getTimestamp("submitted_at")), instant(rs.getTimestamp("graded_at"))),
                userId, limit);
        // Xu hướng điểm: các bài đã chấm gần nhất, cũ → mới
        List<ScorePoint> trend = new ArrayList<>(jdbc.query("""
                        SELECT submission_id, graded_at, score FROM writing_submission
                        WHERE user_id = ? AND grading_status = 'graded'
                        ORDER BY graded_at DESC LIMIT ?
                        """,
                (rs, i) -> new ScorePoint(rs.getObject("submission_id", UUID.class),
                        instant(rs.getTimestamp("graded_at")), rs.getInt("score")),
                userId, limit));
        java.util.Collections.reverse(trend);
        List<CategoryCount> top = jdbc.query("""
                        SELECT i.category, count(*) AS n FROM writing_issue i
                        JOIN writing_submission s ON s.submission_id = i.submission_id
                        WHERE s.user_id = ? AND i.severity = 'error'
                        GROUP BY i.category ORDER BY n DESC, i.category LIMIT 3
                        """,
                (rs, i) -> new CategoryCount(rs.getString("category"), rs.getLong("n")), userId);
        return new History(items, trend, top);
    }

    private SubmissionView toView(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("submission_id", UUID.class);
        Map<String, Integer> criteria = readJson(rs.getString("criteria"), new TypeReference<>() { });
        Map<String, Object> feedback = readJson(rs.getString("feedback"), new TypeReference<>() { });
        List<String> rewrites = new ArrayList<>();
        String comment = null;
        if (feedback != null) {
            comment = (String) feedback.get("comment");
            if (feedback.get("rewrites") instanceof List<?> list) {
                list.forEach(r -> rewrites.add(String.valueOf(r)));
            }
        }
        List<IssueView> issues = jdbc.query("""
                        SELECT issue_id, sentence_index, start_offset, end_offset, original, suggestion, category, severity,
                               explanation_vi
                        FROM writing_issue WHERE submission_id = ? ORDER BY start_offset
                        """,
                (r, i) -> new IssueView(r.getObject("issue_id", UUID.class), r.getInt("sentence_index"),
                        r.getInt("start_offset"), r.getInt("end_offset"), r.getString("original"),
                        r.getString("suggestion"), r.getString("category"), r.getString("severity"),
                        r.getString("explanation_vi")),
                id);
        List<VocabSuggestionView> words = jdbc.query("""
                        SELECT suggestion_id, learner_form, word, reason, meaning_vi, cefr_level, status
                        FROM writing_vocab_suggestion WHERE submission_id = ? ORDER BY created_at, word
                        """,
                (r, i) -> new VocabSuggestionView(r.getObject("suggestion_id", UUID.class), r.getString("learner_form"),
                        r.getString("word"), r.getString("reason"), r.getString("meaning_vi"), r.getString("cefr_level"),
                        r.getString("status")),
                id);
        return new SubmissionView(id, rs.getString("grading_status"), rs.getString("prompt_source"),
                rs.getString("prompt_text"), rs.getString("submitted_text"), rs.getInt("word_count"),
                (Integer) rs.getObject("score"), (Integer) rs.getObject("stars_earned"), criteria, comment, rewrites,
                issues, words, instant(rs.getTimestamp("submitted_at")), instant(rs.getTimestamp("graded_at")),
                instant(rs.getTimestamp("next_retry_at")));
    }

    private static Map<String, Object> criteriaMap(CriterionScores c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("task_achievement", c.taskAchievement());
        map.put("coherence", c.coherence());
        map.put("vocabulary", c.vocabulary());
        map.put("grammar", c.grammar());
        return map;
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize JSON", e);
        }
    }

    private static <T> T readJson(String value, TypeReference<T> type) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse stored JSON", e);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
