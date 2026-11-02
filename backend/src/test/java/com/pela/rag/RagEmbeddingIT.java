package com.pela.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pela.rag.application.port.in.BackfillPromptEmbeddingsUseCase;
import com.pela.rag.application.port.in.EmbedWritingPromptUseCase;
import com.pela.rag.application.port.in.FindDuplicateWritingPromptsUseCase;
import com.pela.rag.application.port.in.FindDuplicateWritingPromptsUseCase.DuplicateWarning;
import com.pela.rag.application.port.in.FindDuplicateWritingPromptsUseCase.NewPrompt;
import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.rag.application.port.out.WritingPromptVectorPort;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.support.PostgresIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RAG.Retrieve / BR-14 / Admin.QuestionBank.Duplicates trên pgvector thật với vector cố định; không gọi Gemini
 * (EmbeddingPort được mock: văn bản lạ nhận một vector cơ sở riêng, gần như trực giao với các vector của test).
 */
@AutoConfigureMockMvc
class RagEmbeddingIT extends PostgresIntegrationTest {

    private static final int DIMS = 768;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    WritingPromptVectorPort vectors;

    @Autowired
    EmbedWritingPromptUseCase embed;

    @Autowired
    BackfillPromptEmbeddingsUseCase backfill;

    @Autowired
    FindDuplicateWritingPromptsUseCase duplicates;

    @Autowired
    MockMvc mvc;

    @Autowired
    com.pela.rag.adapter.in.job.EmbedMissingPromptsJob job;

    @MockitoBean
    EmbeddingPort embedding;

    private final Map<String, float[]> embeddings = new HashMap<>();
    private UUID admin;
    private String topic;

    @BeforeEach
    void setUp() {
        admin = createUser("admin");
        topic = "RagIT-" + UUID.randomUUID();
        when(embedding.embed(anyString(), any())).thenAnswer(inv -> {
            String text = inv.getArgument(0);
            return embeddings.getOrDefault(text, basis(200 + Math.floorMod(text.hashCode(), 500)));
        });
    }

    private UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"Rag IT\"}')",
                id, "rag-" + id + "@example.test");
        jdbc.update("UPDATE users SET role = ?, status = 'active' WHERE user_id = ?", role, id);
        return id;
    }

    private static final java.util.Random NOISE = new java.util.Random(42);

    /**
     * Vector chuẩn hoá L2 từ các thành phần đầu, cộng nhiễu dày rất nhỏ (chuẩn ≈ 0.05). Vector toàn số 0 / trực giao
     * hoàn toàn làm đồ thị HNSW khó duyệt (khoảng cách bằng nhau) — embedding thật luôn dày; nhiễu không đổi thứ tự
     * mà test kỳ vọng (các mức similarity cách nhau ≥ 0.02).
     */
    private static float[] vec(double... head) {
        double[] v = new double[DIMS];
        double headNorm = 0;
        for (double h : head) {
            headNorm += h * h;
        }
        for (int i = 0; i < DIMS; i++) {
            v[i] = (i < head.length ? head[i] / Math.sqrt(headNorm) : 0) + NOISE.nextGaussian() * 0.05 / Math.sqrt(DIMS);
        }
        double norm = 0;
        for (double x : v) {
            norm += x * x;
        }
        float[] out = new float[DIMS];
        for (int i = 0; i < DIMS; i++) {
            out[i] = (float) (v[i] / Math.sqrt(norm));
        }
        return out;
    }

    private static float[] basis(int index) {
        double[] head = new double[index + 1];
        head[index] = 1;
        return vec(head);
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ",").append(v[i]);
        }
        return sb.append(']').toString();
    }

    private UUID insertPrompt(String text, String promptTopic, String level, String status, float[] vector) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, topic, cefr_level,
                                           status, embedding_vector, embedding_model, created_by,
                                           retired_at)
                VALUES (?, ?, 'paragraph', 'writing_prompt', ?, ?, ?, ?::vector, ?, ?, ?)
                """, id, text, promptTopic, level, status, vector == null ? null : literal(vector),
                vector == null ? null : "gemini-embedding-001", admin,
                status.equals("retired") && vector != null ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        return id;
    }

    @Test
    void topKIsOrderedBySimilarityAndLimitedToK() {
        float[] query = vec(1, 0);
        UUID p1 = insertPrompt("p1", topic, "B1", "active", vec(1, 0));
        UUID p2 = insertPrompt("p2", topic, "B1", "active", vec(1, 0.2));
        UUID p3 = insertPrompt("p3", topic, "B1", "active", vec(1, 0.5));
        UUID p4 = insertPrompt("p4", topic, "B1", "active", vec(1, 1));
        UUID p5 = insertPrompt("p5", topic, "B1", "active", vec(0.5, 1));
        insertPrompt("p6", topic, "B1", "active", vec(0, 1));

        List<SimilarWritingPrompt> top = vectors.nearest(query, topic, "B1", 5);

        assertThat(top).extracting(SimilarWritingPrompt::questionId).containsExactly(p1, p2, p3, p4, p5);
        assertThat(top.get(0).similarity()).isCloseTo(1.0, within(0.01));
        assertThat(top.get(3).similarity()).isCloseTo(Math.sqrt(0.5), within(0.01));
        assertThat(top).isSortedAccordingTo((a, b) -> Double.compare(b.similarity(), a.similarity()));
    }

    @Test
    void filtersByTopicAndLevelAndSkipsRetired() {
        float[] query = vec(1, 0);
        UUID match = insertPrompt("match", topic, "B1", "active", vec(1, 0.8));
        insertPrompt("other level", topic, "B2", "active", vec(1, 0));
        insertPrompt("other topic", topic + "-x", "B1", "active", vec(1, 0));
        insertPrompt("retired", topic, "B1", "retired", vec(1, 0));
        // Nhiều đề gần hơn nhưng khác topic: lọc làm hụt K, iterative scan vẫn phải tìm ra đề khớp
        for (int i = 0; i < 60; i++) {
            insertPrompt("near " + i, topic + "-near", "B1", "active", vec(1, 0.01 * i));
        }

        List<SimilarWritingPrompt> top = vectors.nearest(query, topic, "B1", 5);

        assertThat(top).extracting(SimilarWritingPrompt::questionId).containsExactly(match);
        assertThat(top.get(0).topic()).isEqualTo(topic);
        assertThat(top.get(0).cefrLevel()).isEqualTo("B1");
    }

    @Test
    void duplicatesAboveConfiguredThresholdOnly() {
        // import.duplicate_similarity = 0.95
        String text = "Describe your favourite season " + topic;
        embeddings.put(text, vec(0, 0, 1, 0));
        UUID same = insertPrompt("same", topic, "A2", "active", vec(0, 0, 1, 0.1));      // ~0.995
        UUID editing = insertPrompt("editing", topic, "B2", "active", vec(0, 0, 1, 0));  // 1.0, bị loại vì đang sửa
        insertPrompt("related", topic, "A2", "active", vec(0, 0, 1, 0.5));              // ~0.894
        insertPrompt("retired copy", topic, "A2", "retired", vec(0, 0, 1, 0));

        List<SimilarWritingPrompt> found = duplicates.duplicates(text, editing);

        assertThat(found).extracting(SimilarWritingPrompt::questionId).containsExactly(same);
        assertThat(found.get(0).similarity()).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void duplicateInAnotherTopicAndLevelIsReportedWithDetails() {
        String text = "Write about a hobby " + topic;
        embeddings.put(text, vec(0, 0, 0, 0, 0, 0, 0, 1));
        String otherTopic = topic + "-other";
        UUID existing = insertPrompt("existing", otherTopic, "C1", "active", vec(0, 0, 0, 0, 0, 0, 0, 1, 0.05));

        List<DuplicateWarning> warnings = duplicates.checkImport(List.of(new NewPrompt(text, topic, "A2")));

        assertThat(warnings).singleElement().satisfies(w -> {
            assertThat(w.promptIndex()).isZero();
            assertThat(w.inFile()).isEmpty();
            assertThat(w.existing()).singleElement().satisfies(d -> {
                assertThat(d.questionId()).isEqualTo(existing);
                assertThat(d.topic()).isEqualTo(otherTopic);
                assertThat(d.cefrLevel()).isEqualTo("C1");
                assertThat(d.similarity()).isGreaterThanOrEqualTo(0.95).isLessThanOrEqualTo(1.0);
            });
        });
    }

    @Test
    void twoDuplicatePromptsInTheSameFileWarnEachOther() {
        String a = "Email your teacher about homework " + topic;
        String b = "Write an email to your teacher about homework " + topic;
        String c = "Describe your bedroom " + topic;
        embeddings.put(a, vec(0, 0, 0, 0, 0, 0, 0, 0, 1));
        embeddings.put(b, vec(0, 0, 0, 0, 0, 0, 0, 0, 1, 0.1));
        embeddings.put(c, vec(0, 0, 0, 0, 0, 0, 0, 0, 0, 1));

        List<DuplicateWarning> warnings = duplicates.checkImport(List.of(
                new NewPrompt(a, topic, "B1"), new NewPrompt(c, topic, "B1"), new NewPrompt(b, topic + "-x", "B2")));

        assertThat(warnings).extracting(DuplicateWarning::promptIndex).containsExactly(0, 2);
        assertThat(warnings.get(0).existing()).isEmpty();
        assertThat(warnings.get(0).inFile()).singleElement().satisfies(d -> {
            assertThat(d.otherIndex()).isEqualTo(2);
            assertThat(d.topic()).isEqualTo(topic + "-x");
            assertThat(d.cefrLevel()).isEqualTo("B2");
            assertThat(d.similarity()).isGreaterThanOrEqualTo(0.95);
        });
        assertThat(warnings.get(1).inFile()).singleElement()
                .satisfies(d -> assertThat(d.otherIndex()).isZero());
    }

    @Test
    void reembedStoresVectorAndModelInCallerTransaction() {
        UUID id = insertPrompt("Old text", topic, "B1", "active", vec(0, 0, 0, 0, 1));
        String newText = "New text " + topic;
        embeddings.put(newText, vec(0, 0, 0, 0, 0, 1));

        tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE question_bank SET question_text = ?, embedding_model = 'old-model' WHERE question_id = ?",
                    newText, id);
            embed.reembed(id);
        });

        assertThat(jdbc.queryForObject("SELECT embedding_model FROM question_bank WHERE question_id = ?", String.class, id))
                .isEqualTo("gemini-embedding-001");
        assertThat(vectors.nearest(vec(0, 0, 0, 0, 0, 1), topic, "B1", 1))
                .singleElement().satisfies(p -> assertThat(p.similarity()).isCloseTo(1.0, within(0.01)));
    }

    @Test
    void embedFailureRollsBackAdminSave() {
        String text = "Broken " + topic;
        when(embedding.embed(org.mockito.ArgumentMatchers.eq(text), any()))
                .thenThrow(new com.pela.shared.application.port.out.AiCallException(
                        com.pela.shared.application.port.out.AiCallException.Kind.TIMEOUT, "timeout"));

        try {
            tx.executeWithoutResult(s -> {
                var e = embed.embedForStorage(text);
                insertPrompt(text, topic, "B1", "active", e.vector());
            });
        } catch (RuntimeException expected) {
            // transaction của Admin rollback
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_bank WHERE topic = ?", Integer.class, topic)).isZero();
    }

    @Test
    void backfillActivatesNeverActivatedPromptsAndReembedsOldModel() {
        UUID pending = insertPrompt("pending " + topic, topic, "A2", "retired", null);
        UUID adminRetired = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, topic, cefr_level,
                                           status, created_by, retired_at)
                VALUES (?, 'admin retired', 'paragraph', 'writing_prompt', ?, 'A2', 'retired', ?, now())
                """, adminRetired, topic, admin);
        UUID oldModel = insertPrompt("old model " + topic, topic, "A2", "active", vec(0, 0, 0, 1));
        jdbc.update("UPDATE question_bank SET embedding_model = 'text-embedding-004' WHERE question_id = ?", oldModel);
        embeddings.put("pending " + topic, vec(0, 0, 0, 0, 0, 0, 1));

        backfill.backfill();

        Map<String, Object> p = jdbc.queryForMap(
                "SELECT status, embedding_model, embedding_vector IS NOT NULL AS has_vector FROM question_bank WHERE question_id = ?",
                pending);
        assertThat(p).containsEntry("status", "active").containsEntry("embedding_model", "gemini-embedding-001")
                .containsEntry("has_vector", true);
        assertThat(jdbc.queryForObject("SELECT embedding_model FROM question_bank WHERE question_id = ?", String.class,
                oldModel)).isEqualTo("gemini-embedding-001");
        assertThat(jdbc.queryForMap("SELECT status, embedding_vector IS NULL AS no_vector FROM question_bank WHERE question_id = ?",
                adminRetired)).containsEntry("status", "retired").containsEntry("no_vector", true);
        assertThat(vectors.nearest(vec(0, 0, 0, 0, 0, 0, 1), topic, "A2", 1))
                .extracting(SimilarWritingPrompt::questionId).containsExactly(pending);
    }

    @Test
    void backfillEndpointIsAdminOnly() throws Exception {
        UUID learner = createUser("learner");

        mvc.perform(post("/v1/admin/question-bank/embeddings/backfill").with(jwt().jwt(j -> j.subject(learner.toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/question-bank/embeddings/backfill").with(jwt().jwt(j -> j.subject(admin.toString()))))
                .andExpect(status().isAccepted());
        // Chờ lượt chạy nền xong để không chen vào test khác
        for (int i = 0; i < 100 && job.isRunning(); i++) {
            Thread.sleep(100);
        }
        assertThat(job.isRunning()).isFalse();
    }
}
