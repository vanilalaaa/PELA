package com.pela.writing;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.support.PostgresIntegrationTest;
import com.pela.writing.application.port.in.PickCuratedPromptUseCase;
import com.pela.writing.application.port.in.PickCuratedPromptUseCase.CuratedPrompt;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** BR-7: đề có sẵn của kho cho một topic + level, dựa trên lịch sử nộp bài thật của learner. */
class CuratedPromptIT extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PickCuratedPromptUseCase curated;

    private UUID admin;
    private UUID learner;
    private String topic;
    private UUID p1;
    private UUID p2;
    private UUID p3;

    @BeforeEach
    void setUp() {
        topic = "CuratedIT-" + UUID.randomUUID();
        admin = createUser();
        learner = createUser();
        p1 = insertPrompt("first prompt", "email", "active", "2026-10-01T00:00:00Z");
        p2 = insertPrompt("second prompt", "paragraph", "active", "2026-10-02T00:00:00Z");
        p3 = insertPrompt("third prompt", "essay", "active", "2026-10-03T00:00:00Z");
        insertPrompt("retired prompt", "email", "retired", "2026-10-04T00:00:00Z");
        jdbc.update("""
                INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, topic, cefr_level,
                                           status, embedding_vector, embedding_model, created_by)
                VALUES (gen_random_uuid(), 'other level', 'email', 'writing_prompt', ?, 'B1', 'active',
                        array_fill(0.1::real, ARRAY[768])::vector, 'test-model', ?)
                """, topic, admin);
    }

    @Test
    void neverPicksAPromptTheLearnerAlreadySubmittedWhileOthersRemain() {
        submit(p1, "graded", "2026-10-10T00:00:00Z");
        submit(p2, "draft", "2026-10-11T00:00:00Z"); // nháp không tính là đã làm

        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            seen.add(curated.pick(learner, topic, "A2").orElseThrow().questionId());
        }

        assertThat(seen).containsExactlyInAnyOrder(p2, p3);
    }

    @Test
    void whenEverythingIsDoneItReturnsThePromptDoneLongestAgo() {
        submit(p1, "graded", "2026-10-20T00:00:00Z");
        submit(p2, "pending", "2026-10-10T00:00:00Z");
        submit(p3, "failed", "2026-10-15T00:00:00Z");

        CuratedPrompt picked = curated.pick(learner, topic, "A2").orElseThrow();

        assertThat(picked.questionId()).isEqualTo(p2);
        assertThat(picked.done()).isTrue();
        assertThat(picked.taskType()).isEqualTo("paragraph");
        assertThat(picked.minWords()).isEqualTo(20);
        assertThat(picked.maxWords()).isEqualTo(40);
    }

    @Test
    void anotherLearnersHistoryDoesNotMatter() {
        submit(p1, "graded", "2026-10-10T00:00:00Z");
        UUID other = createUser();

        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            seen.add(curated.pick(other, topic, "A2").orElseThrow().questionId());
        }

        assertThat(seen).contains(p1);
    }

    @Test
    void noActivePromptForTheTopicAndLevelGivesNothing() {
        assertThat(curated.pick(learner, topic + "-none", "A2")).isEmpty();
        assertThat(curated.pick(learner, topic, "C2")).isEmpty();
    }

    @Test
    void suggestionsPutUndonePromptsFirstAndSkipRetiredOnes() {
        submit(p1, "graded", "2026-10-10T00:00:00Z");

        List<CuratedPrompt> suggestions = curated.suggestions(learner, topic, "A2", 10);

        assertThat(suggestions).extracting(CuratedPrompt::questionId).containsExactly(p2, p3, p1);
        assertThat(suggestions).extracting(CuratedPrompt::done).containsExactly(false, false, true);
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"Curated IT\"}')",
                id, "curated-" + id + "@example.test");
        jdbc.update("UPDATE users SET status = 'active', cefr_level = 'A2' WHERE user_id = ?", id);
        return id;
    }

    private UUID insertPrompt(String text, String type, String status, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, payload, topic,
                                           cefr_level, status, embedding_vector, embedding_model, created_by, created_at,
                                           retired_at)
                VALUES (?, ?, ?, 'writing_prompt', '{"min_words":20,"max_words":40}'::jsonb, ?, 'A2', ?,
                        array_fill(0.1::real, ARRAY[768])::vector, 'test-model', ?, ?::timestamptz,
                        CASE WHEN ? = 'retired' THEN now() END)
                """, id, text, type, topic, status, admin, createdAt, status);
        return id;
    }

    private void submit(UUID promptId, String status, String createdAt) {
        jdbc.update("""
                INSERT INTO writing_submission (submission_id, user_id, prompt_question_id, prompt_source, submitted_text,
                                                word_count, grading_status, submitted_at)
                VALUES (gen_random_uuid(), ?, ?, 'bank', 'some text', 25, ?, CASE WHEN ? = 'draft' THEN NULL ELSE ?::timestamptz END)
                """, learner, promptId, status, status, createdAt);
    }
}
