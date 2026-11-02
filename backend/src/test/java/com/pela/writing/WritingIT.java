package com.pela.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.LlmPort.LlmResult;
import com.pela.support.PostgresIntegrationTest;
import com.pela.writing.application.port.in.GradeWritingUseCase;
import com.pela.writing.application.usecase.GradingResponse;
import com.pela.writing.application.usecase.GradingResponse.Criteria;
import com.pela.writing.application.usecase.GradingResponse.Issue;
import com.pela.writing.application.usecase.GradingResponse.VocabCandidate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** UC-10 / UC-23 qua HTTP với DB thật; chỉ LLM được mock. Job chấm được gọi trực tiếp (pela.writing.grade-immediately=false). */
@AutoConfigureMockMvc
class WritingIT extends PostgresIntegrationTest {

    private static final String TEXT = "I goes to school every day. My school is big and I bought a souvenir there. "
            + "I like my school very much because my teachers are kind and my friends are funny.";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GradeWritingUseCase grading;

    @MockitoBean
    LlmPort llm;

    private UUID learner;
    private UUID souvenirId;

    @BeforeEach
    void setUp() {
        learner = createLearner();
        jdbc.update("""
                INSERT INTO vocabulary (word, meaning, cefr_level) VALUES ('souvenir', 'quà lưu niệm', 'A2')
                ON CONFLICT DO NOTHING
                """);
        souvenirId = jdbc.queryForObject(
                "SELECT vocab_id FROM vocabulary WHERE lower(word) = 'souvenir' AND owner_user_id IS NULL", UUID.class);
    }

    private UUID createLearner() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"IT Writer\"}')",
                id, "writer-" + id + "@example.test");
        jdbc.update("UPDATE users SET cefr_level = 'B1' WHERE user_id = ?", id);
        return id;
    }

    private ResultActions submit(UUID user, UUID submissionId, String text) throws Exception {
        return mvc.perform(post("/v1/practice/writing/submissions")
                .with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"submissionId\":\"" + submissionId + "\",\"promptSource\":\"freeform\",\"text\":\"" + text + "\"}"));
    }

    private ResultActions getAs(UUID user, String url) throws Exception {
        return mvc.perform(get(url).with(jwt().jwt(j -> j.subject(user.toString()))));
    }

    private static GradingResponse response() {
        return new GradingResponse(new Criteria(22, 20, 18, 16),
                List.of(new Issue(0, "goes", "go", "grammar", "error", "Chủ ngữ \"I\" đi với động từ nguyên mẫu."),
                        new Issue(2, "very much", "a lot", "style", "error", "Cách nói tự nhiên hơn.")),
                List.of("I go to school every day."),
                List.of(new VocabCandidate("souvenir", "souvenir", "misused", "quà lưu niệm"),
                        new VocabCandidate("recieve", "receive", "misspelled", "nhận")),
                "Bài viết rõ ý, cần chú ý chia động từ.");
    }

    @Test
    void submitGradeAndReadResult() throws Exception {
        UUID id = UUID.randomUUID();
        when(llm.generateJson(eq("writing_grading"), anyMap(), eq(GradingResponse.class), any(Duration.class)))
                .thenReturn(new LlmResult<>(response(), 2, "gemini-3.8-flash"));

        submit(learner, id, TEXT).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.submissionId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("pending"));
        // Gửi lại cùng id: không tạo bài thứ hai
        submit(learner, id, TEXT).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("pending"));

        grading.gradeOne(id);

        int goesAt = TEXT.indexOf("goes");
        getAs(learner, "/v1/practice/writing/submissions/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("graded"))
                .andExpect(jsonPath("$.score").value(76))
                .andExpect(jsonPath("$.starsEarned").value(4))
                .andExpect(jsonPath("$.criteria.task_achievement").value(22))
                .andExpect(jsonPath("$.comment").value("Bài viết rõ ý, cần chú ý chia động từ."))
                .andExpect(jsonPath("$.issues.length()").value(2))
                .andExpect(jsonPath("$.issues[0].startOffset").value(goesAt))
                .andExpect(jsonPath("$.issues[0].endOffset").value(goesAt + 4))
                .andExpect(jsonPath("$.issues[1].severity").value("suggestion"))
                .andExpect(jsonPath("$.vocabSuggestions.length()").value(1))
                .andExpect(jsonPath("$.vocabSuggestions[0].word").value("receive"))
                .andExpect(jsonPath("$.vocabSuggestions[0].status").value("suggested"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT prompt_version, model, grading_attempts FROM writing_submission WHERE submission_id = ?", id);
        assertThat(row).containsEntry("prompt_version", 2).containsEntry("model", "gemini-3.8-flash")
                .containsEntry("grading_attempts", 1);

        // "souvenir" trùng learner_form nên không phải từ đáng học; thêm "receive" vào hàng ôn
        String suggestionId = jdbc.queryForObject(
                "SELECT suggestion_id::text FROM writing_vocab_suggestion WHERE submission_id = ? AND word = 'receive'",
                String.class, id);
        mvc.perform(post("/v1/practice/writing/vocab-suggestions/" + suggestionId + "/add")
                        .with(jwt().jwt(j -> j.subject(learner.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("added"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM review_card c JOIN vocabulary v ON v.vocab_id = c.vocab_id
                WHERE c.user_id = ? AND v.word = 'receive' AND v.owner_user_id = ? AND c.in_pool AND c.origin = 'writing'
                """, Integer.class, learner, learner)).isEqualTo(1);

        getAs(learner, "/v1/practice/writing/submissions").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].submissionId").value(id.toString()))
                .andExpect(jsonPath("$.scoreTrend[0].score").value(76))
                .andExpect(jsonPath("$.topIssueCategories[0].category").value("grammar"));

        // Bài của người khác: 404
        getAs(createLearner(), "/v1/practice/writing/submissions/" + id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_NOT_FOUND"));
    }

    @Test
    void catalogueWordIsPushedToReviewToday() throws Exception {
        UUID id = UUID.randomUUID();
        GradingResponse r = response();
        when(llm.generateJson(eq("writing_grading"), anyMap(), eq(GradingResponse.class), any(Duration.class)))
                .thenReturn(new LlmResult<>(new GradingResponse(r.criteria(), List.of(), List.of(),
                        List.of(new VocabCandidate("sovenir", "souvenir", "misspelled", "quà lưu niệm")), "Ok."),
                        2, "m"));

        submit(learner, id, TEXT).andExpect(status().isAccepted());
        grading.gradeOne(id);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM writing_vocab_suggestion WHERE submission_id = ? AND word = 'souvenir'", String.class, id))
                .isEqualTo("auto_added");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM review_card WHERE user_id = ? AND vocab_id = ? AND in_pool", Integer.class, learner,
                souvenirId)).isEqualTo(1);
    }

    @Test
    void twoAiFailuresQueueRetry() throws Exception {
        UUID id = UUID.randomUUID();
        when(llm.generateJson(eq("writing_grading"), anyMap(), eq(GradingResponse.class), any(Duration.class)))
                .thenThrow(new AiCallException(Kind.TIMEOUT, "timeout"));

        submit(learner, id, TEXT).andExpect(status().isAccepted());
        Instant before = Instant.now();
        grading.gradeOne(id);

        getAs(learner, "/v1/practice/writing/submissions/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("retry_queued"));
        Instant nextRetry = jdbc.queryForObject("SELECT next_retry_at FROM writing_submission WHERE submission_id = ?",
                java.sql.Timestamp.class, id).toInstant();
        assertThat(nextRetry).isAfter(before.plus(Duration.ofMinutes(4)));
        // Chưa tới next_retry_at: job không nhận lại
        grading.gradeOne(id);
        assertThat(jdbc.queryForObject("SELECT grading_attempts FROM writing_submission WHERE submission_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void quotaExhaustedReturns429AndKeepsDraft() throws Exception {
        for (int i = 0; i < 5; i++) {
            jdbc.update("""
                    INSERT INTO writing_submission (user_id, prompt_source, submitted_text, word_count, grading_status,
                                                    score, submitted_at, graded_at)
                    VALUES (?, 'freeform', 'x', 25, 'graded', 50, now(), now())
                    """, learner);
        }
        UUID id = UUID.randomUUID();

        submit(learner, id, TEXT).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("WRITING_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.error.details.status").value("draft"))
                .andExpect(jsonPath("$.error.details.dailyLimit").value(5));

        assertThat(jdbc.queryForObject("SELECT grading_status FROM writing_submission WHERE submission_id = ?",
                String.class, id)).isEqualTo("draft");
    }

    @Test
    void rejectsTooShortText() throws Exception {
        submit(learner, UUID.randomUUID(), "Too short.").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("WORD_COUNT_OUT_OF_RANGE"))
                .andExpect(jsonPath("$.error.details.wordCount").value(2));
    }
}
