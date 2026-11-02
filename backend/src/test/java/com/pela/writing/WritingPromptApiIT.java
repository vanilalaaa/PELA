package com.pela.writing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.rag.application.usecase.GeneratedPromptResponse;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.LlmPort.LlmResult;
import com.pela.support.PostgresIntegrationTest;
import java.util.Random;
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

/** UC-24 qua HTTP với DB thật: tạo đề AI, nộp bài bằng đề đó, dự phòng đề kho (BR-7). LLM và embedding được mock. */
@AutoConfigureMockMvc
class WritingPromptApiIT extends PostgresIntegrationTest {

    private static final int DIMS = 768;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    LlmPort llm;

    @MockitoBean
    EmbeddingPort embedding;

    private String topic;
    private UUID admin;
    private UUID learner;
    private UUID bankPrompt;

    @BeforeEach
    void setUp() {
        topic = "ApiIT-" + UUID.randomUUID();
        admin = createUser(null);
        learner = createUser("A2");
        bankPrompt = insertBankPrompt("Write an email to a friend about your trip.", vec(1, 0.4));
        insertBankPrompt("Describe a holiday you enjoyed.", vec(1, 0.7));

        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_QUERY))).thenReturn(vec(1, 0));
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_DOCUMENT))).thenReturn(vec(0, 1));
        when(llm.generateJson(eq("rag_question_generation"), any(), eq(GeneratedPromptResponse.class), any()))
                .thenReturn(new LlmResult<>(new GeneratedPromptResponse("Write an email to a hotel about a booking.",
                        "email", 30, 100), 2, "gemini-test"));
    }

    private ResultActions generate(UUID user, String body) throws Exception {
        return mvc.perform(post("/v1/practice/writing/prompts/generate")
                .with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void generatesAnAiPromptThenTheLearnerSubmitsAnEssayWithIt() throws Exception {
        String response = generate(learner, "{\"topic\":\"" + topic + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptSource").value("ai_generated"))
                .andExpect(jsonPath("$.promptText").value("Write an email to a hotel about a booking."))
                .andExpect(jsonPath("$.taskType").value("email"))
                .andExpect(jsonPath("$.minWords").value(30))
                .andExpect(jsonPath("$.maxWords").value(100))
                .andExpect(jsonPath("$.cacheHit").value(false))
                .andExpect(jsonPath("$.questionId").doesNotExist())
                .andExpect(jsonPath("$.fallbackReason").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode choice = JSON.readTree(response);
        UUID cacheId = UUID.fromString(choice.get("ragCacheId").asText());

        UUID submissionId = UUID.randomUUID();
        mvc.perform(post("/v1/practice/writing/submissions")
                        .with(jwt().jwt(j -> j.subject(learner.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":\"" + submissionId + "\",\"promptSource\":\"ai_generated\","
                                + "\"ragCacheId\":\"" + cacheId + "\",\"text\":\"" + "word ".repeat(40).strip() + "\"}"))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForMap("""
                SELECT prompt_source, rag_cache_id, prompt_question_id, prompt_text FROM writing_submission
                WHERE submission_id = ?
                """, submissionId)).containsEntry("prompt_source", "ai_generated").containsEntry("rag_cache_id", cacheId)
                .containsEntry("prompt_question_id", null)
                .containsEntry("prompt_text", "Write an email to a hotel about a booking.");
    }

    @Test
    void aiPromptServedToSomeoneElseCannotBeSubmitted() throws Exception {
        String response = generate(learner, "{\"topic\":\"" + topic + "\"}").andReturn().getResponse()
                .getContentAsString();
        String cacheId = JSON.readTree(response).get("ragCacheId").asText();
        UUID other = createUser("A2");

        mvc.perform(post("/v1/practice/writing/submissions")
                        .with(jwt().jwt(j -> j.subject(other.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submissionId\":\"" + UUID.randomUUID() + "\",\"promptSource\":\"ai_generated\","
                                + "\"ragCacheId\":\"" + cacheId + "\",\"text\":\"" + "word ".repeat(40).strip() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROMPT_NOT_FOUND"));
    }

    @Test
    void whenTheLlmTimesOutTheLearnerStillGetsABankPromptWithTheReason() throws Exception {
        when(llm.generateJson(eq("rag_question_generation"), any(), eq(GeneratedPromptResponse.class), any()))
                .thenThrow(new AiCallException(Kind.TIMEOUT, "slow"));

        generate(learner, "{\"topic\":\"" + topic + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptSource").value("bank"))
                .andExpect(jsonPath("$.questionId").isNotEmpty())
                .andExpect(jsonPath("$.ragCacheId").doesNotExist())
                .andExpect(jsonPath("$.fallbackReason").value("TIMEOUT"))
                .andExpect(jsonPath("$.taskType").value("email"));
        assertThat(jdbc.queryForMap("SELECT outcome, reason FROM rag_generation_log WHERE user_id = ? AND topic = ?",
                learner, topic)).containsEntry("outcome", "fallback").containsEntry("reason", "TIMEOUT");
    }

    @Test
    void topicWithoutAnyBankPromptIsNotFound() throws Exception {
        generate(learner, "{\"topic\":\"" + topic + "-empty\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NO_PROMPT_AVAILABLE"));
    }

    @Test
    void learnerWithoutALevelMustChooseOneFirst() throws Exception {
        UUID noLevel = createUser(null);

        generate(noLevel, "{\"topic\":\"" + topic + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LEVEL_REQUIRED"));
    }

    @Test
    void invalidRequestsAndMissingTokenAreRejected() throws Exception {
        generate(learner, "{\"topic\":\" \"}").andExpect(status().is4xxClientError());
        generate(learner, "{\"topic\":\"" + topic + "\",\"taskType\":\"poem\"}").andExpect(status().is4xxClientError());
        mvc.perform(post("/v1/practice/writing/prompts/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"topic\":\"" + topic + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/practice/writing/prompts").param("topic", topic)).andExpect(status().isUnauthorized());
    }

    @Test
    void suggestionsListTheBankPromptsOfTheTopicAtTheLearnersLevel() throws Exception {
        mvc.perform(get("/v1/practice/writing/prompts").param("topic", topic).param("limit", "5")
                        .with(jwt().jwt(j -> j.subject(learner.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].done").value(false))
                .andExpect(jsonPath("$[0].text").isNotEmpty())
                .andExpect(jsonPath("$[?(@.questionId == '" + bankPrompt + "')]").exists());
    }

    private UUID createUser(String level) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"Api IT\"}')",
                id, "prompt-api-" + id + "@example.test");
        jdbc.update("UPDATE users SET status = 'active', cefr_level = ? WHERE user_id = ?", level, id);
        return id;
    }

    private UUID insertBankPrompt(String text, float[] vector) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, payload, topic,
                                           cefr_level, status, embedding_vector, embedding_model, created_by)
                VALUES (?, ?, 'email', 'writing_prompt', '{"min_words":30,"max_words":100}'::jsonb, ?, 'A2', 'active',
                        ?::vector, 'gemini-embedding-001', ?)
                """, id, text, topic, literal(vector), admin);
        return id;
    }

    private static final Random NOISE = new Random(11);

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
