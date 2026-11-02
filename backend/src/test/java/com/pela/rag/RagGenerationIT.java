package com.pela.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pela.rag.application.port.in.ExpireRagCacheUseCase;
import com.pela.rag.application.port.in.GenerateWritingPromptUseCase;
import com.pela.rag.application.port.in.GenerateWritingPromptUseCase.GenerateCommand;
import com.pela.rag.application.port.in.GenerationResult;
import com.pela.rag.application.port.in.GenerationResult.Generated;
import com.pela.rag.application.port.in.GenerationResult.NeedsFallback;
import com.pela.rag.application.port.in.GenerationResult.Source;
import com.pela.rag.application.port.in.GetServedPromptUseCase;
import com.pela.rag.application.usecase.GeneratedPromptResponse;
import com.pela.rag.domain.FallbackReason;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.LlmPort.LlmResult;
import com.pela.support.PostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * UC-24 trên pgvector thật với vector cố định: Top-K, cache (BR-24), hết hạn (BR-25), giới hạn ngày và nhật ký. LLM và
 * embedding được mock (không gọi Gemini): truy vấn nhận vector (1,0), đề do "LLM" viết nhận vector (0,1).
 */
class RagGenerationIT extends PostgresIntegrationTest {

    private static final int DIMS = 768;
    private static final Random NOISE = new Random(7);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GenerateWritingPromptUseCase generate;

    @Autowired
    GetServedPromptUseCase servedPrompts;

    @Autowired
    ExpireRagCacheUseCase expire;

    @Autowired
    ClockPort clock;

    @MockitoBean
    EmbeddingPort embedding;

    @MockitoBean
    LlmPort llm;

    private UUID admin;
    private UUID learner;
    private UUID otherLearner;
    private UUID top1;
    private String topic;

    @BeforeEach
    void setUp() {
        topic = "GenIT-" + UUID.randomUUID();
        admin = createUser("admin", null);
        learner = createUser("learner", "A2");
        otherLearner = createUser("learner", "A2");
        jdbc.update("INSERT INTO learner_profile (user_id, interests) VALUES (?, ARRAY['food','music'])", learner);

        top1 = insertBankPrompt("Write an email to a friend about your trip.", "email", vec(1, 0.5));
        insertBankPrompt("Describe a holiday you enjoyed.", "email", vec(1, 0.8));
        insertBankPrompt("Write about a place you want to visit.", "email", vec(1, 1.2));

        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_QUERY))).thenReturn(vec(1, 0));
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_DOCUMENT))).thenReturn(vec(0, 1));
        when(llm.generateJson(eq("rag_question_generation"), any(), eq(GeneratedPromptResponse.class), any()))
                .thenReturn(new LlmResult<>(new GeneratedPromptResponse("Write an email to a hotel about a booking.",
                        "email", 50, 100), 2, "gemini-test"));
    }

    private GenerateCommand command(String taskType) {
        return new GenerateCommand(topic, "A2", taskType, clock.now().plusMillis(4000));
    }

    @Test
    void generatesStoresTheCacheEntryLogsAndOnlyTheLearnerCanReadItBack() {
        GenerationResult result = generate.generate(learner, command(null));

        Generated generated = (Generated) result;
        assertThat(generated.source()).isEqualTo(Source.GENERATED);
        UUID cacheId = generated.prompt().cacheId();
        assertThat(jdbc.queryForMap("""
                SELECT topic, cefr_level, question_type, status, source_template_id, hit_count,
                       generated_question->>'prompt_text' AS prompt_text,
                       (expires_at - created_at) >= interval '29 days 23 hours' AS ttl_ok
                FROM rag_question_cache WHERE cache_id = ?
                """, cacheId)).containsEntry("topic", topic).containsEntry("cefr_level", "A2")
                .containsEntry("question_type", "email").containsEntry("status", "active")
                .containsEntry("source_template_id", top1).containsEntry("hit_count", 0)
                .containsEntry("prompt_text", "Write an email to a hotel about a booking.").containsEntry("ttl_ok", true);
        assertThat(count("SELECT count(*) FROM rag_cache_serve WHERE cache_id = '" + cacheId + "' AND user_id = '"
                + learner + "'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM question_bank WHERE question_text LIKE 'Write an email to a hotel%'"))
                .isZero(); // BR-25: đề AI không bao giờ vào question_bank

        assertThat(servedPrompts.servedTo(learner, cacheId)).contains(generated.prompt());
        assertThat(servedPrompts.servedTo(otherLearner, cacheId)).isEmpty();
        assertThat(jdbc.queryForMap("SELECT outcome, reason, top1_similarity > 0.8 AS close, latency_ms >= 0 AS ok "
                + "FROM rag_generation_log WHERE user_id = ? AND topic = ?", learner, topic))
                .containsEntry("outcome", "generated").containsEntry("reason", null)
                .containsEntry("close", true).containsEntry("ok", true);
    }

    @Test
    void queryIsBuiltFromTopicLevelAndTheLearnersInterests() {
        generate.generate(learner, command(null));

        verify(embedding).embed(
                eq("Writing prompt. Topic: " + topic + ". CEFR level: A2. Learner interests: food, music."),
                eq(TaskType.RETRIEVAL_QUERY));
    }

    @Test
    void anotherLearnerHitsTheCacheButTheSameLearnerDoesNotGetItAgainWithin14Days() {
        Generated first = (Generated) generate.generate(learner, command(null));
        clearInvocations(llm);

        // BR-24: cùng learner không nhận lại đề đã phát trong 14 ngày -> sinh đề mới
        Generated again = (Generated) generate.generate(learner, command(null));
        assertThat(again.source()).isEqualTo(Source.GENERATED);
        assertThat(again.prompt().cacheId()).isNotEqualTo(first.prompt().cacheId());
        verify(llm, times(1)).generateJson(anyString(), any(), eq(GeneratedPromptResponse.class), any());
        clearInvocations(llm);

        // Learner khác: cache hit, không gọi LLM
        Generated hit = (Generated) generate.generate(otherLearner, command(null));
        assertThat(hit.source()).isEqualTo(Source.CACHE_HIT);
        verifyNoInteractions(llm);
        assertThat(count("SELECT hit_count FROM rag_question_cache WHERE cache_id = '" + hit.prompt().cacheId() + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM rag_cache_serve WHERE user_id = '" + otherLearner + "'")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT outcome FROM rag_generation_log WHERE topic = ? ORDER BY created_at, log_id",
                String.class, topic)).containsExactlyInAnyOrder("generated", "generated", "cache_hit");
    }

    @Test
    void cacheLookupHonoursTheRequestedTaskType() {
        generate.generate(learner, command("email"));
        clearInvocations(llm);

        // Cache chỉ có đề email: learner khác xin essay thì không hit (LLM trả đề email nên sai dạng -> fallback)
        GenerationResult result = generate.generate(otherLearner, command("essay"));

        assertThat(result).isEqualTo(NeedsFallback.of(FallbackReason.INVALID_OUTPUT));
    }

    @Test
    void retiringTheSourceTemplateExpiresItsCacheEntries() {
        Generated first = (Generated) generate.generate(learner, command(null));
        clearInvocations(llm);

        assertThat(expire.expireBySourceTemplate(top1)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM rag_question_cache WHERE cache_id = ?", String.class,
                first.prompt().cacheId())).isEqualTo("expired");
        // Entry hết hạn không còn là cache hit; vẫn giữ lại để audit và vẫn đọc lại được cho người đã nhận
        assertThat(((Generated) generate.generate(otherLearner, command(null))).source()).isEqualTo(Source.GENERATED);
        assertThat(servedPrompts.servedTo(learner, first.prompt().cacheId())).isPresent();
        // Chỉ còn entry mới (do lần sinh vừa rồi) đang active; entry cũ đã expired nên không bị đếm lại
        assertThat(expire.expireBySourceTemplate(top1)).isEqualTo(1);
        assertThat(expire.expireBySourceTemplate(top1)).isZero();
    }

    @Test
    void entriesPastTheirTtlAreExpiredByTheDailyJobUseCase() {
        Generated first = (Generated) generate.generate(learner, command(null));
        jdbc.update("UPDATE rag_question_cache SET expires_at = now() - interval '1 day' WHERE cache_id = ?",
                first.prompt().cacheId());

        assertThat(expire.expireDue()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM rag_question_cache WHERE cache_id = ?", String.class,
                first.prompt().cacheId())).isEqualTo("expired");
    }

    @Test
    void dailyLimitFallsBackWithoutCallingAnyAiService() {
        UUID entry = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rag_question_cache (cache_id, query_embedding, topic, cefr_level, question_type,
                                                generated_question, expires_at)
                VALUES (?, ?::vector, ?, 'A2', 'email', '{"prompt_text":"x","min_words":50,"max_words":100}'::jsonb,
                        now() + interval '30 days')
                """, entry, literal(vec(0, 0, 1)), topic);
        for (int i = 0; i < 10; i++) { // writing.ai_prompt_daily_limit = 10
            jdbc.update("INSERT INTO rag_cache_serve (cache_id, user_id, served_at) VALUES (?, ?, ?)", entry, learner,
                    Timestamp.from(clock.now().minus(Duration.ofSeconds(i + 1))));
        }

        GenerationResult result = generate.generate(learner, command(null));

        assertThat(result).isEqualTo(NeedsFallback.of(FallbackReason.DAILY_LIMIT));
        verifyNoInteractions(llm);
        verify(embedding, times(0)).embed(anyString(), any());
        assertThat(jdbc.queryForMap("SELECT outcome, reason FROM rag_generation_log WHERE user_id = ? AND topic = ?",
                learner, topic)).containsEntry("outcome", "fallback").containsEntry("reason", "DAILY_LIMIT");
    }

    @Test
    void emptyBankOfTheRequestedTopicFallsBackAndLogsTheReason() {
        GenerationResult result = generate.generate(learner,
                new GenerateCommand(topic + "-empty", "A2", null, clock.now().plusMillis(4000)));

        assertThat(result).isEqualTo(NeedsFallback.of(FallbackReason.BELOW_THRESHOLD));
        verifyNoInteractions(llm);
        assertThat(jdbc.queryForMap("SELECT reason, top1_similarity FROM rag_generation_log WHERE topic = ?",
                topic + "-empty")).containsEntry("reason", "BELOW_THRESHOLD").containsEntry("top1_similarity", null);
    }

    @Test
    void promptThatCopiesABankPromptIsRejectedAndNothingIsCached() {
        // "LLM" viết đúng lại một đề kho: vector của nó trùng vector đề top1 (cosine ≈ 1 >= 0.95)
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_DOCUMENT))).thenReturn(vec(1, 0.5));

        GenerationResult result = generate.generate(learner, command(null));

        assertThat(result).isEqualTo(NeedsFallback.of(FallbackReason.INVALID_OUTPUT));
        assertThat(count("SELECT count(*) FROM rag_question_cache WHERE topic = '" + topic + "'")).isZero();
    }

    @Test
    void examplesGivenToTheLlmComeFromTheBankWithTaskTypeAndWordRange() {
        generate.generate(learner, command(null));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, String>> vars = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(llm).generateJson(eq("rag_question_generation"), vars.capture(), eq(GeneratedPromptResponse.class), any());
        assertThat(vars.getValue().get("examples").lines().toList()).containsExactly(
                "1. [email, 50-100 words] Write an email to a friend about your trip.",
                "2. [email, 50-100 words] Describe a holiday you enjoyed.",
                "3. [email, 50-100 words] Write about a place you want to visit.");
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    private UUID createUser(String role, String level) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"Gen IT\"}')",
                id, "gen-" + id + "@example.test");
        jdbc.update("UPDATE users SET role = ?, status = 'active', cefr_level = ? WHERE user_id = ?", role, level, id);
        return id;
    }

    private UUID insertBankPrompt(String text, String type, float[] vector) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, payload, topic,
                                           cefr_level, status, embedding_vector, embedding_model, created_by)
                VALUES (?, ?, ?, 'writing_prompt', '{"min_words":50,"max_words":100}'::jsonb, ?, 'A2', 'active',
                        ?::vector, 'gemini-embedding-001', ?)
                """, id, text, type, topic, literal(vector), admin);
        return id;
    }

    /** Vector chuẩn hoá L2 từ các thành phần đầu cộng nhiễu dày rất nhỏ (như RagEmbeddingIT) để HNSW duyệt được. */
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

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ",").append(v[i]);
        }
        return sb.append(']').toString();
    }
}
