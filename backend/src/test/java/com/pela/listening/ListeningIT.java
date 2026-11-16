package com.pela.listening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pela.admin.application.port.in.ListeningImportJobUseCase;
import com.pela.shared.application.port.out.AdminAuditPort;
import com.pela.shared.application.port.out.StoragePort;
import com.pela.shared.application.port.out.TtsPort;
import com.pela.shared.application.port.out.TtsPort.SynthesizedAudio;
import com.pela.support.PostgresIntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * UC-19 nhập Listening từ CSV → TTS → lưu, và UC-11 luyện nghe (BR-14, BR-16, BR-34, BR-35, BR-36, BR-37) trên DB thật.
 * TtsPort và StoragePort được mock (Storage là map trong bộ nhớ); không gọi Gemini / Supabase.
 */
@AutoConfigureMockMvc
class ListeningIT extends PostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ListeningImportJobUseCase imports;

    @MockitoBean
    TtsPort tts;

    @MockitoBean
    StoragePort storage;

    @MockitoSpyBean
    AdminAuditPort audit;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final AtomicInteger ttsCalls = new AtomicInteger();
    private UUID admin;
    private UUID learner;
    private String topic;

    @BeforeEach
    void setUp() {
        admin = createUser("admin");
        learner = createUser("learner");
        topic = "ListeningIT-" + UUID.randomUUID().toString().substring(0, 8);
        objects.clear();
        doAnswer(inv -> {
            objects.put(inv.getArgument(0) + "/" + inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(storage).upload(anyString(), anyString(), any(), anyString());
        when(storage.download(anyString(), anyString())).thenAnswer(inv -> {
            byte[] content = objects.get(inv.getArgument(0) + "/" + inv.getArgument(1));
            if (content == null) {
                throw new StoragePort.StorageException("not found", null);
            }
            return content;
        });
        doAnswer(inv -> {
            byte[] content = objects.get(inv.getArgument(0) + "/" + inv.getArgument(1));
            if (content == null) {
                throw new StoragePort.StorageException("not found", null);
            }
            objects.put(inv.getArgument(2) + "/" + inv.getArgument(3), content);
            return null;
        }).when(storage).copy(anyString(), anyString(), anyString(), anyString());
        doAnswer(inv -> objects.remove(inv.getArgument(0) + "/" + inv.getArgument(1)))
                .when(storage).delete(anyString(), anyString());
        when(storage.signedUrl(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> "https://storage.test/" + inv.getArgument(0) + "/" + inv.getArgument(1) + "?token=t");
        when(tts.synthesize(any(), any(), any(), anyInt())).thenAnswer(inv -> {
            ttsCalls.incrementAndGet();
            return new SynthesizedAudio(wav(24000, 48000 * 3), Duration.ofSeconds(3));
        });
        doCallRealMethod().when(audit).record(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void importPreviewTtsAndSaveCreatesClipsAndQuestions() throws Exception {
        UUID jobId = uploadAndProcess(templateCsv());
        JsonNode job = getJob(jobId);
        assertThat(job.path("status").asText()).isEqualTo("preview_ready");
        assertThat(job.path("warnings")).isEmpty();
        assertThat(job.path("clips")).hasSize(2);
        assertThat(job.at("/clips/0/clip/lines/0/row").asInt()).isEqualTo(2);
        assertThat(objects).containsKey("imports/jobs/" + jobId + "/source.csv");

        // Chưa có audio → không lưu được (BR-37)
        mvc.perform(admin(post("/v1/admin/imports/{id}/save", jobId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUDIO_OUT_OF_DATE"));

        tts(jobId, 0);
        tts(jobId, 1);
        JsonNode afterTts = getJob(jobId);
        assertThat(afterTts.path("attempts").asInt()).isEqualTo(2);
        assertThat(afterTts.at("/clips/0/audio/current").asBoolean()).isTrue();

        // Admin sửa lời thoại bài 0 → audio cũ không còn khớp, lưu bị chặn đến khi sinh lại
        ObjectNode edited = (ObjectNode) afterTts.deepCopy();
        ArrayNode clips = JSON.createArrayNode();
        afterTts.path("clips").forEach(c -> clips.add(c.path("clip").deepCopy()));
        ((ObjectNode) clips.get(0).path("lines").get(1)).put("en", "Sure. That's five dollars.");
        JsonNode patched = patchPreview(jobId, clips);
        assertThat(patched.at("/clips/0/audio/current").asBoolean()).isFalse();
        assertThat(patched.at("/clips/1/audio/current").asBoolean()).isTrue();
        assertThat(edited).isNotNull();
        mvc.perform(admin(post("/v1/admin/imports/{id}/save", jobId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUDIO_OUT_OF_DATE"))
                .andExpect(jsonPath("$.error.details.clipIndexes[0]").value(0));

        tts(jobId, 0);
        JsonNode saved = json(mvc.perform(admin(post("/v1/admin/imports/{id}/save", jobId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.path("clipIds")).hasSize(2);
        UUID clipId = UUID.fromString(saved.at("/clipIds/0").asText());

        Map<String, Object> clip = jdbc.queryForMap(
                "SELECT title, topic, cefr_level, order_index, audio_url, duration_sec, voice_config::text AS voices,"
                        + " tts_model, audio_format, status, import_job_id FROM listening_clip WHERE clip_id = ?", clipId);
        assertThat(clip.get("title")).isEqualTo("At the bakery");
        assertThat(clip.get("order_index")).isEqualTo(1);
        assertThat(clip.get("duration_sec")).isEqualTo(3);
        assertThat((String) clip.get("voices")).contains("\"Linh\": \"Kore\"").contains("\"Baker\": \"Puck\"");
        assertThat(clip.get("tts_model")).isEqualTo(jdbc.queryForObject(
                "SELECT value #>> '{}' FROM system_setting WHERE setting_key = 'ai.model.tts'", String.class));
        assertThat(clip.get("audio_format")).isEqualTo("wav");
        assertThat(clip.get("import_job_id")).isEqualTo(jobId);
        assertThat(objects).containsKey("listening-audio/" + clip.get("audio_url"));
        assertThat(jdbc.queryForObject("SELECT order_index FROM listening_clip WHERE clip_id = ?", Integer.class,
                UUID.fromString(saved.at("/clipIds/1").asText()))).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT question_purpose || ':' || status || ':' || (payload->>'answer') "
                + "FROM question_bank WHERE clip_id = ? ORDER BY created_at", String.class, clipId))
                .containsExactly("listening:active:B", "listening:active:C");
        assertThat(jdbc.queryForObject("SELECT status FROM import_job WHERE job_id = ?", String.class, jobId))
                .isEqualTo("saved");
        // BR-35: mỗi bài + chính lần lưu đều có nhật ký
        assertThat(jdbc.queryForList("SELECT action_type FROM admin_action_log WHERE admin_id = ? ORDER BY acted_at",
                String.class, admin)).containsExactlyInAnyOrder("listening_clip.create", "listening_clip.create",
                "import_job.save");

        // Lưu lần hai bị từ chối
        mvc.perform(admin(post("/v1/admin/imports/{id}/save", jobId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IMPORT_NOT_EDITABLE"));
    }

    @Test
    void saveIsAtomicAndRemovesCopiedAudioWhenTheTransactionFails() throws Exception {
        UUID jobId = uploadAndProcess(templateCsv());
        tts(jobId, 0);
        tts(jobId, 1);
        doThrow(new IllegalStateException("audit down")).when(audit)
                .record(eq(admin), eq("import_job.save"), anyString(), any(), any());

        mvc.perform(admin(post("/v1/admin/imports/{id}/save", jobId)))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM listening_clip WHERE import_job_id = ?", Integer.class, jobId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_action_log WHERE admin_id = ?", Integer.class, admin))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM import_job WHERE job_id = ?", String.class, jobId))
                .isEqualTo("preview_ready");
        assertThat(objects.keySet()).noneMatch(k -> k.startsWith("listening-audio/"));
    }

    @Test
    void invalidFilesAreRejectedWithRowErrors() throws Exception {
        // Thiếu cột → job failed, lỗi theo cột
        String missingColumn = templateCsv().replaceFirst(",explanation\\r?\\n", "\n");
        UUID failed = uploadAndProcess(missingColumn);
        JsonNode job = getJob(failed);
        assertThat(job.path("status").asText()).isEqualTo("failed");
        assertThat(job.at("/warnings/0/code").asText()).isEqualTo("MISSING_COLUMN");

        // Đáp án E → preview có lỗi theo dòng; không sinh audio, không lưu
        String badAnswer = templateCsv().replace("Three,Four,B,", "Three,Four,E,");
        UUID jobId = uploadAndProcess(badAnswer);
        JsonNode preview = getJob(jobId);
        assertThat(preview.path("status").asText()).isEqualTo("preview_ready");
        assertThat(preview.at("/warnings/0/code").asText()).isEqualTo("INVALID_ANSWER");
        assertThat(preview.at("/warnings/0/row").asInt()).isEqualTo(5);
        assertThat(preview.at("/warnings/0/column").asText()).isEqualTo("answer");
        mvc.perform(admin(post("/v1/admin/imports/{id}/clips/0/tts", jobId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LISTENING_CLIP_INVALID"));
        mvc.perform(admin(post("/v1/admin/imports/{id}/save", jobId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IMPORT_HAS_ERRORS"));

        // Sai loại file / quá dung lượng
        mvc.perform(admin(multipart("/v1/admin/imports").file(new MockMultipartFile("file", "clips.pdf",
                "application/pdf", "x".getBytes())).param("kind", "listening_csv")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        mvc.perform(admin(multipart("/v1/admin/imports").file(new MockMultipartFile("file", "clips.csv", "text/csv", big))
                .param("kind", "listening_csv")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
        assertThat(ttsCalls.get()).isZero();
    }

    @Test
    void adminEndpointsRequireAdminAndTemplateIsDownloadable() throws Exception {
        mvc.perform(get("/v1/admin/imports/templates/listening").with(as(learner)))
                .andExpect(status().isForbidden());
        mvc.perform(admin(get("/v1/admin/imports/templates/listening")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("clip_code,title,topic,cefr_level,row_type")));
    }

    @Test
    void learnerPracticeHidesAnswersAndReplayAddsOnlyTheImprovement() throws Exception {
        List<UUID> clipIds = importTemplate();
        UUID clipId = clipIds.getFirst();

        mvc.perform(get("/v1/practice/listening/topics").param("level", "A1").with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.topic == '" + topic + "')].clipCount").value(2));
        mvc.perform(get("/v1/practice/listening/clips").param("topic", topic).param("level", "A1").with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clipId").value(clipId.toString()))
                .andExpect(jsonPath("$[0].orderIndex").value(1))
                .andExpect(jsonPath("$[1].orderIndex").value(2))
                .andExpect(jsonPath("$[0].bestStars").doesNotExist());

        String detail = mvc.perform(get("/v1/practice/listening/clips/{id}", clipId).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioUrl").value(containsString("listening-audio/" + clipId)))
                .andReturn().getResponse().getContentAsString();
        // Trước khi nộp: không lộ đáp án, giải thích, transcript
        assertThat(detail).doesNotContain("answer\"").doesNotContain("explanation").doesNotContain("transcript")
                .doesNotContain("croissants, please");
        JsonNode questions = json(detail).path("questions");
        assertThat(questions).hasSize(2);
        UUID q1 = UUID.fromString(questions.get(0).path("questionId").asText());
        UUID q2 = UUID.fromString(questions.get(1).path("questionId").asText());

        // Phiên 1: đúng 1/2 = 50% → 2 sao, cộng 2
        UUID first = UUID.randomUUID();
        JsonNode r1 = submit(first, clipId, Map.of(q1, "B", q2, "A"));
        assertThat(r1.path("stars").asInt()).isEqualTo(2);
        assertThat(r1.path("starsAdded").asInt()).isEqualTo(2);
        assertThat(r1.at("/results/0/correctAnswer").asText()).isEqualTo("B");
        assertThat(r1.at("/results/1/explanation").asText()).contains("four dollars");
        assertThat(r1.at("/transcript/0/vi").asText()).startsWith("Chào buổi sáng");

        // Gửi lại cùng sessionId: trả kết quả cũ, không chấm lại
        JsonNode again = submit(first, clipId, Map.of(q1, "B", q2, "C"));
        assertThat(again.path("stars").asInt()).isEqualTo(2);
        assertThat(again.path("starsAdded").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listening_session WHERE user_id = ?", Integer.class, learner))
                .isEqualTo(1);

        // Làm lại 100% → 5 sao, chỉ cộng 3 (BR-36)
        JsonNode r2 = submit(UUID.randomUUID(), clipId, Map.of(q1, "B", q2, "C"));
        assertThat(r2.path("stars").asInt()).isEqualTo(5);
        assertThat(r2.path("previousBest").asInt()).isEqualTo(2);
        assertThat(r2.path("starsAdded").asInt()).isEqualTo(3);

        // Làm lại kém hơn → cộng 0
        JsonNode r3 = submit(UUID.randomUUID(), clipId, Map.of(q1, "A", q2, "A"));
        assertThat(r3.path("stars").asInt()).isZero();
        assertThat(r3.path("starsAdded").asInt()).isZero();
        assertThat(r3.path("previousBest").asInt()).isEqualTo(5);

        // Phiên của learner này không dùng được cho người khác
        UUID other = createUser("learner");
        mvc.perform(post("/v1/practice/listening/sessions").with(as(other)).contentType(MediaType.APPLICATION_JSON)
                .content(body(first, clipId, Map.of(q1, "B", q2, "C"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_ID_CONFLICT"));
        // Thiếu câu trả lời
        mvc.perform(post("/v1/practice/listening/sessions").with(as(learner)).contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), clipId, Map.of(q1, "B"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ANSWERS_MISMATCH"));

        mvc.perform(get("/v1/practice/listening/clips").param("topic", topic).param("level", "A1").with(as(learner)))
                .andExpect(jsonPath("$[0].bestStars").value(5));
    }

    @Test
    void editingTranscriptRequiresNewAudioAndRetireKeepsHistory() throws Exception {
        UUID clipId = importTemplate().getFirst();
        JsonNode detail = json(mvc.perform(admin(get("/v1/admin/listening/clips/{id}", clipId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ArrayNode lines = (ArrayNode) detail.path("lines").deepCopy();
        ((ObjectNode) lines.get(1)).put("en", "Sure. That's six dollars.");
        ObjectNode change = JSON.createObjectNode();
        change.set("lines", lines);
        change.put("title", "At the bakery (v2)");

        // Không kèm audio mới → 409
        mvc.perform(admin(patch("/v1/admin/listening/clips/{id}", clipId)).contentType(MediaType.APPLICATION_JSON)
                .content(change.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUDIO_REGENERATION_REQUIRED"));

        ObjectNode ttsRequest = JSON.createObjectNode();
        ttsRequest.set("voices", detail.path("voices"));
        ttsRequest.set("lines", lines);
        JsonNode preview = json(mvc.perform(admin(post("/v1/admin/listening/clips/{id}/tts", clipId))
                .contentType(MediaType.APPLICATION_JSON).content(ttsRequest.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        change.put("audioToken", preview.path("audioToken").asText());
        JsonNode updated = json(mvc.perform(admin(patch("/v1/admin/listening/clips/{id}", clipId))
                .contentType(MediaType.APPLICATION_JSON).content(change.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.path("title").asText()).isEqualTo("At the bakery (v2)");
        assertThat(updated.at("/lines/1/en").asText()).isEqualTo("Sure. That's six dollars.");
        assertThat(updated.path("audioFingerprint").asText()).isEqualTo(preview.path("fingerprint").asText());
        assertThat(jdbc.queryForObject("SELECT audio_url FROM listening_clip WHERE clip_id = ?", String.class, clipId))
                .isEqualTo(clipId + "/" + preview.path("fingerprint").asText() + ".wav");

        // Learner làm bài, rồi Admin retire: bài ẩn khỏi danh sách nhưng lịch sử còn nguyên
        JsonNode questions = json(mvc.perform(get("/v1/practice/listening/clips/{id}", clipId).with(as(learner)))
                .andReturn().getResponse().getContentAsString()).path("questions");
        UUID sessionId = UUID.randomUUID();
        submit(sessionId, clipId, Map.of(UUID.fromString(questions.get(0).path("questionId").asText()), "B",
                UUID.fromString(questions.get(1).path("questionId").asText()), "C"));

        mvc.perform(admin(post("/v1/admin/listening/clips/{id}/retire", clipId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("retired"));
        mvc.perform(get("/v1/practice/listening/clips").param("topic", topic).param("level", "A1").with(as(learner)))
                .andExpect(jsonPath("$[?(@.clipId == '" + clipId + "')]").isEmpty());
        mvc.perform(get("/v1/practice/listening/clips/{id}", clipId).with(as(learner)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM listening_attempt a JOIN listening_session s"
                + " ON s.session_id = a.session_id WHERE s.clip_id = ?", Integer.class, clipId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM question_bank WHERE clip_id = ? AND status = 'retired'",
                Integer.class, clipId)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT action_type FROM admin_action_log WHERE target_id = ? ORDER BY acted_at",
                String.class, clipId.toString())).containsExactly("listening_clip.create", "listening_clip.update",
                "listening_clip.retire");
        // Gửi lại phiên cũ sau khi retire vẫn trả kết quả
        JsonNode replay = submit(sessionId, clipId, Map.of());
        assertThat(replay.path("stars").asInt()).isEqualTo(5);
    }

    private List<UUID> importTemplate() throws Exception {
        UUID jobId = uploadAndProcess(templateCsv());
        tts(jobId, 0);
        tts(jobId, 1);
        JsonNode saved = json(mvc.perform(admin(post("/v1/admin/imports/{id}/save", jobId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<UUID> ids = new ArrayList<>();
        saved.path("clipIds").forEach(id -> ids.add(UUID.fromString(id.asText())));
        return ids;
    }

    private UUID uploadAndProcess(String csv) throws Exception {
        JsonNode job = json(mvc.perform(admin(multipart("/v1/admin/imports")
                        .file(new MockMultipartFile("file", "clips.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("kind", "listening_csv")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"))
                .andReturn().getResponse().getContentAsString());
        UUID jobId = UUID.fromString(job.path("jobId").asText());
        imports.process(jobId);
        return jobId;
    }

    private void tts(UUID jobId, int index) throws Exception {
        mvc.perform(admin(post("/v1/admin/imports/{id}/clips/{idx}/tts", jobId, index)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSec").value(3))
                .andExpect(jsonPath("$.audioUrl").value(containsString("imports/jobs/" + jobId)));
    }

    private JsonNode patchPreview(UUID jobId, ArrayNode clips) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.set("clips", clips);
        return json(mvc.perform(admin(patch("/v1/admin/imports/{id}/preview", jobId))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJob(UUID jobId) throws Exception {
        return json(mvc.perform(admin(get("/v1/admin/imports/{id}", jobId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode submit(UUID sessionId, UUID clipId, Map<UUID, String> answers) throws Exception {
        return json(mvc.perform(post("/v1/practice/listening/sessions").with(as(learner))
                .contentType(MediaType.APPLICATION_JSON).content(body(sessionId, clipId, answers.isEmpty()
                        ? Map.of(UUID.randomUUID(), "A") : answers)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String body(UUID sessionId, UUID clipId, Map<UUID, String> answers) {
        ObjectNode body = JSON.createObjectNode();
        body.put("sessionId", sessionId.toString());
        body.put("clipId", clipId.toString());
        ArrayNode list = body.putArray("answers");
        answers.forEach((q, a) -> list.addObject().put("questionId", q.toString()).put("selectedAnswer", a));
        return body.toString();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(as(admin));
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }

    private String templateCsv() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/templates/listening-import-template.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("Daily life", topic);
        }
    }

    private static JsonNode json(String body) throws IOException {
        return JSON.readTree(body);
    }

    private UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES (?, ?, '{\"full_name\":\"Listening IT\"}')",
                id, "listening-" + id + "@example.test");
        jdbc.update("UPDATE users SET role = ?, status = 'active' WHERE user_id = ?", role, id);
        return id;
    }

    private static byte[] wav(int sampleRate, int dataBytes) {
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(sampleRate)
                .putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataBytes);
        return b.array();
    }
}
