package com.pela.listening.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.listening.application.port.out.ListeningClipPort;
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
public class JdbcListeningClipAdapter implements ListeningClipPort {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Bài learner chọn được: đã phát hành, có audio, còn ít nhất một câu hỏi active. */
    private static final String PLAYABLE = """
            c.status = 'published' AND c.audio_url NOT LIKE 'pending://%'
            AND EXISTS (SELECT 1 FROM question_bank q WHERE q.clip_id = c.clip_id AND q.status = 'active')
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcListeningClipAdapter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Map<String, Integer> topics(String level) {
        Map<String, Integer> topics = new LinkedHashMap<>();
        jdbc.query("SELECT c.topic, count(*) FROM listening_clip c WHERE c.cefr_level = ? AND " + PLAYABLE
                + " GROUP BY c.topic ORDER BY c.topic", rs -> {
                    topics.put(rs.getString(1), rs.getInt(2));
                }, level);
        return topics;
    }

    @Override
    public List<PlayableClip> playableClips(String topic, String level) {
        return jdbc.query("""
                SELECT c.clip_id, c.title, c.order_index, c.duration_sec,
                       (SELECT count(*) FROM question_bank q WHERE q.clip_id = c.clip_id AND q.status = 'active')
                FROM listening_clip c
                WHERE c.cefr_level = ? AND c.topic = ?
                """ + " AND " + PLAYABLE + " ORDER BY c.order_index",
                (rs, i) -> new PlayableClip(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                        (Integer) rs.getObject(4), rs.getInt(5)),
                level, topic);
    }

    @Override
    public Optional<StoredClip> find(UUID clipId) {
        List<StoredQuestion> questions = jdbc.query("""
                SELECT question_id, question_type, question_text, payload::text, status
                FROM question_bank WHERE clip_id = ? AND question_purpose = 'listening'
                ORDER BY created_at, question_id
                """, (rs, i) -> {
                    JsonNode payload = read(rs.getString(4), JsonNode.class);
                    List<String> options = new ArrayList<>();
                    payload.path("options").forEach(o -> options.add(o.asText()));
                    return new StoredQuestion(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), options,
                            payload.path("answer").asText(null), payload.path("explanation").asText(null),
                            rs.getString(5));
                }, clipId);
        return jdbc.query("""
                SELECT clip_id, title, topic, cefr_level, order_index, audio_url, duration_sec, transcript::text,
                       voice_config::text, tts_model, audio_format, status, retired_at
                FROM listening_clip WHERE clip_id = ?
                """, (rs, i) -> new StoredClip(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getString(6), (Integer) rs.getObject(7),
                read(rs.getString(8), new TypeReference<List<Line>>() { }),
                read(rs.getString(9), new TypeReference<LinkedHashMap<String, String>>() { }),
                rs.getString(10), rs.getString(11), rs.getString(12),
                rs.getTimestamp(13) == null ? null : rs.getTimestamp(13).toInstant(), questions), clipId)
                .stream().findFirst();
    }

    @Override
    public int insert(NewClip clip) {
        return tx.execute(status -> {
            // Hai lần lưu đồng thời cùng topic + cấp không lấy trùng order_index
            jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {
            }, "listening_clip:" + clip.cefrLevel() + ":" + clip.topic());
            Integer next = jdbc.queryForObject(
                    "SELECT COALESCE(max(order_index), 0) + 1 FROM listening_clip WHERE cefr_level = ? AND topic = ?",
                    Integer.class, clip.cefrLevel(), clip.topic());
            jdbc.update("""
                    INSERT INTO listening_clip (clip_id, topic, title, cefr_level, order_index, audio_url, duration_sec,
                                                transcript, voice_config, tts_model, audio_format, status,
                                                import_job_id, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, 'published', ?, ?, ?)
                    """, clip.clipId(), clip.topic(), clip.title(), clip.cefrLevel(), next, clip.audioPath(),
                    clip.durationSec(), write(clip.transcript()), write(clip.voices()), clip.ttsModel(),
                    clip.audioFormat(), clip.importJobId(), clip.createdBy(), Timestamp.from(clip.createdAt()));
            // created_at tăng dần theo thứ tự câu hỏi trong file để find() giữ đúng thứ tự
            for (int i = 0; i < clip.questions().size(); i++) {
                NewQuestion q = clip.questions().get(i);
                jdbc.update("""
                        INSERT INTO question_bank (question_id, clip_id, question_text, question_type, question_purpose,
                                                   payload, topic, cefr_level, status, import_job_id, created_by,
                                                   created_at)
                        VALUES (?, ?, ?, ?, 'listening', ?::jsonb, ?, ?, 'active', ?, ?, ?)
                        """, q.questionId(), clip.clipId(), q.text(), q.type(), write(payload(q.options(), q.answer(),
                        q.explanation())), clip.topic(), clip.cefrLevel(), clip.importJobId(), clip.createdBy(),
                        Timestamp.from(clip.createdAt().plusMillis(i)));
            }
            return next;
        });
    }

    @Override
    public void update(UUID clipId, ClipChange change, List<QuestionChange> questions) {
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE listening_clip
                    SET title = ?, transcript = ?::jsonb, voice_config = ?::jsonb,
                        audio_url = COALESCE(?, audio_url), duration_sec = COALESCE(?, duration_sec),
                        tts_model = COALESCE(?, tts_model), audio_format = COALESCE(?, audio_format)
                    WHERE clip_id = ?
                    """, change.title(), write(change.transcript()), write(change.voices()), change.audioPath(),
                    change.durationSec(), change.ttsModel(), change.audioFormat(), clipId);
            for (QuestionChange q : questions) {
                jdbc.update("""
                        UPDATE question_bank SET question_text = ?, payload = ?::jsonb
                        WHERE question_id = ? AND clip_id = ?
                        """, q.text(), write(payload(q.options(), q.answer(), q.explanation())), q.questionId(), clipId);
            }
        });
    }

    @Override
    public void retire(UUID clipId, Instant at) {
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE listening_clip SET status = 'retired', retired_at = ? WHERE clip_id = ? AND status <> 'retired'",
                    Timestamp.from(at), clipId);
            jdbc.update("UPDATE question_bank SET status = 'retired', retired_at = ? WHERE clip_id = ? AND status = 'active'",
                    Timestamp.from(at), clipId);
        });
    }

    private static Map<String, Object> payload(List<String> options, String answer, String explanation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("options", options);
        payload.put("answer", answer);
        payload.put("explanation", explanation);
        return payload;
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T read(String json, Class<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
