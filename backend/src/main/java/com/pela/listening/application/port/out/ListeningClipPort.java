package com.pela.listening.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bảng listening_clip + question_bank (question_purpose = listening). */
public interface ListeningClipPort {

    record Line(String speaker, String en, String vi) {
    }

    record StoredQuestion(UUID questionId, String type, String text, List<String> options, String answer,
            String explanation, String status) {
    }

    /** @param audioPath đường dẫn trong bucket listening-audio; {@code pending://} = chưa có audio */
    record StoredClip(UUID clipId, String title, String topic, String cefrLevel, int orderIndex, String audioPath,
            Integer durationSec, List<Line> transcript, Map<String, String> voices, String ttsModel,
            String audioFormat, String status, Instant retiredAt, List<StoredQuestion> questions) {
    }

    /** Bài nghe learner chọn được: published và đã có audio. */
    record PlayableClip(UUID clipId, String title, int orderIndex, Integer durationSec, int questionCount) {
    }

    record NewQuestion(UUID questionId, String type, String text, List<String> options, String answer,
            String explanation) {
    }

    record NewClip(UUID clipId, String title, String topic, String cefrLevel, String audioPath, int durationSec,
            List<Line> transcript, Map<String, String> voices, String ttsModel, String audioFormat, UUID importJobId,
            UUID createdBy, Instant createdAt, List<NewQuestion> questions) {
    }

    record ClipChange(String title, List<Line> transcript, Map<String, String> voices, String audioPath,
            Integer durationSec, String ttsModel, String audioFormat) {
    }

    record QuestionChange(UUID questionId, String text, List<String> options, String answer, String explanation) {
    }

    /** Topic có bài nghe chơi được ở cấp này, kèm số bài. */
    Map<String, Integer> topics(String level);

    /** Sắp theo order_index (Listening.Select.Source). */
    List<PlayableClip> playableClips(String topic, String level);

    Optional<StoredClip> find(UUID clipId);

    /** Thêm bài nghe + câu hỏi với order_index = max + 1 trong (cefr_level, topic); trả order_index. */
    int insert(NewClip clip);

    void update(UUID clipId, ClipChange change, List<QuestionChange> questions);

    /** Retire bài nghe và câu hỏi của nó; không xoá gì (BR-34). */
    void retire(UUID clipId, Instant at);
}
