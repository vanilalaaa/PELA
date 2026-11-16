package com.pela.listening.adapter.out.persistence;

import com.pela.listening.application.port.out.ListeningSessionPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcListeningSessionAdapter implements ListeningSessionPort {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcListeningSessionAdapter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<StoredSession> find(UUID sessionId) {
        List<Attempt> attempts = jdbc.query("""
                SELECT attempt_id, question_id, selected_answer, is_correct FROM listening_attempt
                WHERE session_id = ? ORDER BY attempted_at, attempt_id
                """, (rs, i) -> new Attempt(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getBoolean(4)), sessionId);
        return jdbc.query("""
                SELECT session_id, user_id, clip_id, total_questions, correct_count, COALESCE(stars_earned, 0),
                       completed_at
                FROM listening_session WHERE session_id = ?
                """, (rs, i) -> new StoredSession(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant(), attempts), sessionId)
                .stream().findFirst();
    }

    @Override
    public Map<UUID, Integer> bestStars(UUID userId, Collection<UUID> clipIds) {
        Map<UUID, Integer> best = new HashMap<>();
        if (clipIds.isEmpty()) {
            return best;
        }
        jdbc.query("""
                SELECT clip_id, max(stars_earned) FROM listening_session
                WHERE user_id = ? AND clip_id = ANY (?) AND completed_at IS NOT NULL
                GROUP BY clip_id
                """, rs -> {
                    best.put(rs.getObject(1, UUID.class), rs.getInt(2));
                }, userId, clipIds.toArray(new UUID[0]));
        return best;
    }

    @Override
    public int bestStarsBefore(UUID userId, UUID clipId, UUID excludeSessionId, Instant before) {
        Integer best = jdbc.queryForObject("""
                SELECT COALESCE(max(stars_earned), 0) FROM listening_session
                WHERE user_id = ? AND clip_id = ? AND session_id <> ? AND completed_at IS NOT NULL AND completed_at < ?
                """, Integer.class, userId, clipId, excludeSessionId, Timestamp.from(before));
        return best == null ? 0 : best;
    }

    @Override
    public SaveOutcome save(NewSession s) {
        return tx.execute(status -> {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {
            }, "listening_session:" + s.userId() + ":" + s.clipId());
            Integer previousBest = jdbc.queryForObject("""
                    SELECT COALESCE(max(stars_earned), 0) FROM listening_session
                    WHERE user_id = ? AND clip_id = ? AND completed_at IS NOT NULL
                    """, Integer.class, s.userId(), s.clipId());
            int inserted = jdbc.update("""
                    INSERT INTO listening_session (session_id, user_id, clip_id, total_questions, correct_count,
                                                   stars_earned, started_at, completed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (session_id) DO NOTHING
                    """, s.sessionId(), s.userId(), s.clipId(), s.totalQuestions(), s.correctCount(), s.stars(),
                    Timestamp.from(s.startedAt()), Timestamp.from(s.completedAt()));
            if (inserted == 0) {
                return new SaveOutcome(false, previousBest == null ? 0 : previousBest);
            }
            for (Attempt a : s.attempts()) {
                jdbc.update("""
                        INSERT INTO listening_attempt (attempt_id, session_id, question_id, selected_answer, is_correct,
                                                       attempted_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, a.attemptId(), s.sessionId(), a.questionId(), a.selectedAnswer(), a.correct(),
                        Timestamp.from(s.completedAt()));
            }
            return new SaveOutcome(true, previousBest == null ? 0 : previousBest);
        });
    }
}
