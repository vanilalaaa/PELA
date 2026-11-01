package com.pela.platform.adapter.out.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.TtsPort.SpeechLine;
import com.pela.shared.domain.vector.VectorMath;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiEmbeddingAndTtsAdapterTest {

    private static final String BASE = "https://gemini.test/v1beta";

    private final Map<String, String> raw = new HashMap<>(Map.of(
            "ai.model.embedding", "\"gemini-embedding-001\"",
            "ai.embedding.dimensions", "768",
            "ai.model.tts", "\"gemini-test-tts\""));
    private final SettingsPort settings = key -> Optional.ofNullable(raw.get(key));

    private MockRestServiceServer server;
    private GeminiEmbeddingAdapter embedding;
    private GeminiTtsAdapter tts;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("x-goog-api-key", "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        embedding = new GeminiEmbeddingAdapter(client, settings, Duration.ofSeconds(2));
        tts = new GeminiTtsAdapter(client, settings, Duration.ofSeconds(2));
    }

    // ---------------------------------------------------------------- embedding

    private static String embeddingResponse(int size, double value) {
        String values = IntStream.range(0, size).mapToObj(i -> String.valueOf(value)).collect(Collectors.joining(","));
        return "{\"embedding\":{\"values\":[" + values + "]}}";
    }

    @Test
    void embedSendsTaskTypeAndDimensionsAndReturnsNormalizedVector() {
        server.expect(requestTo(BASE + "/models/gemini-embedding-001:embedContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.content.parts[0].text").value("Write about your last holiday"))
                .andExpect(jsonPath("$.taskType").value("RETRIEVAL_DOCUMENT"))
                .andExpect(jsonPath("$.outputDimensionality").value(768))
                // embedContentConfig bị API thật bỏ qua (trả 3072 chiều) nên không được dùng
                .andExpect(jsonPath("$.embedContentConfig").doesNotExist())
                .andRespond(withSuccess(embeddingResponse(768, 0.5), MediaType.APPLICATION_JSON));

        float[] vector = embedding.embed("Write about your last holiday", TaskType.RETRIEVAL_DOCUMENT);

        assertThat(vector).hasSize(768);
        // Gemini trả vector chưa chuẩn hoá ở 768 chiều (norm = 0.5 * sqrt(768) ≈ 13.9); adapter phải chuẩn hoá
        assertThat(VectorMath.l2Norm(vector)).isCloseTo(1.0, within(1e-5));
        assertThat(vector[0]).isCloseTo((float) (1 / Math.sqrt(768)), within(1e-6f));
        server.verify();
    }

    @Test
    void embedWrongDimensionIsUpstreamError() {
        server.expect(requestTo(BASE + "/models/gemini-embedding-001:embedContent"))
                .andRespond(withSuccess(embeddingResponse(3072, 0.1), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> embedding.embed("x", TaskType.RETRIEVAL_QUERY))
                .satisfies(e -> assertThat(((AiCallException) e).kind()).isEqualTo(Kind.UPSTREAM_ERROR));
    }

    @Test
    void embedRateLimited() {
        server.expect(requestTo(BASE + "/models/gemini-embedding-001:embedContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> embedding.embed("x", TaskType.RETRIEVAL_QUERY))
                .satisfies(e -> assertThat(((AiCallException) e).kind()).isEqualTo(Kind.RATE_LIMITED));
    }

    // ---------------------------------------------------------------- TTS

    /** WAV PCM 16-bit mono với số mẫu cho trước. */
    static byte[] wav(int sampleRate, int samples) {
        int dataSize = samples * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        return b.array();
    }

    private static String interactionWithAudio(byte[] wav) {
        return """
                {"id":"i-1","status":"completed","steps":[
                  {"type":"model_output","content":[{"type":"audio","mime_type":"audio/wav","data":"%s"}]}
                ]}
                """.formatted(Base64.getEncoder().encodeToString(wav));
    }

    @Test
    void multiSpeakerRequestFollowsInteractionsSpeechFormat() {
        server.expect(requestTo(BASE + "/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.model").value("gemini-test-tts"))
                .andExpect(jsonPath("$.input[0].type").value("user_input"))
                .andExpect(jsonPath("$.input[0].content.length()").value(2))
                .andExpect(jsonPath("$.input[0].content[0].type").value("text"))
                .andExpect(jsonPath("$.input[0].content[0].text").value("Can I see your passport?"))
                .andExpect(jsonPath("$.input[0].content[0].annotations[0].type").value("speech_metadata"))
                .andExpect(jsonPath("$.input[0].content[0].annotations[0].speaker").value("Agent"))
                .andExpect(jsonPath("$.input[0].content[0].annotations[0].style").value("Speak slowly and clearly"))
                .andExpect(jsonPath("$.input[0].content[1].annotations[0].speaker").value("Traveller"))
                .andExpect(jsonPath("$.response_format.type").value("audio"))
                .andExpect(jsonPath("$.response_format.mime_type").value("audio/wav"))
                .andExpect(jsonPath("$.response_format.sample_rate").value(24000))
                .andExpect(jsonPath("$.generation_config.speech_config.speakers[0].speaker").value("Agent"))
                .andExpect(jsonPath("$.generation_config.speech_config.speakers[0].voice").value("Kore"))
                .andExpect(jsonPath("$.generation_config.speech_config.speakers[1].speaker").value("Traveller"))
                .andExpect(jsonPath("$.generation_config.speech_config.speakers[1].voice").value("Puck"))
                .andRespond(withSuccess(interactionWithAudio(wav(24000, 36000)), MediaType.APPLICATION_JSON));

        var audio = tts.synthesize(
                List.of(new SpeechLine("Agent", "Can I see your passport?"), new SpeechLine("Traveller", "Sure, here you are.")),
                Map.of("Agent", "Kore", "Traveller", "Puck"), "Speak slowly and clearly", 24000);

        assertThat(audio.duration()).isEqualTo(Duration.ofMillis(1500));
        assertThat(new String(audio.wav(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        server.verify();
    }

    @Test
    void singleSpeakerUsesVoiceArray() {
        server.expect(requestTo(BASE + "/interactions"))
                .andExpect(jsonPath("$.generation_config.speech_config[0].voice").value("Kore"))
                .andExpect(jsonPath("$.input[0].content[0].annotations[0].speaker").doesNotExist())
                .andExpect(jsonPath("$.input[0].content[0].annotations[0].style").value("Natural pace"))
                .andRespond(withSuccess(interactionWithAudio(wav(24000, 24000)), MediaType.APPLICATION_JSON));

        var audio = tts.synthesize(List.of(new SpeechLine("Narrator", "Welcome to PELA.")),
                Map.of("Narrator", "Kore"), "Natural pace", 24000);

        assertThat(audio.duration()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void structuredVariantUsesStructuredSpeechConfigForOneAndTwoSpeakers() {
        var structured = new GeminiTtsAdapter(RestClient.create(), settings, Duration.ofSeconds(2),
                GeminiTtsAdapter.Variant.STRUCTURED_SPEECH_CONFIG);
        var two = List.of(new SpeechLine("Agent", "Hi"), new SpeechLine("Traveller", "Hello"));

        var body = structured.requestBody("m", two, Map.of("Agent", "Kore", "Traveller", "Puck"), null, 24000);
        assertThat(body.path("generation_config").has("speech_config")).isFalse();
        assertThat(body.at("/generation_config/structured_speech_config/speakers/0/speaker").asText()).isEqualTo("Agent");
        assertThat(body.at("/generation_config/structured_speech_config/speakers/1/voice").asText()).isEqualTo("Puck");

        var one = structured.requestBody("m", List.of(new SpeechLine("N", "Hi")), Map.of("N", "Kore"), null, 24000);
        assertThat(one.at("/generation_config/structured_speech_config/speakers/0/voice").asText()).isEqualTo("Kore");
    }

    @Test
    void ttsHttpErrorMessageCarriesShortUpstreamReason() {
        server.expect(requestTo(BASE + "/interactions")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\",\"message\":\"Unknown name speech_config\"}}"));

        assertThatThrownBy(() -> tts.synthesize(List.of(new SpeechLine("A", "Hello")), Map.of("A", "Kore"), null, 24000))
                .isInstanceOf(AiCallException.class)
                .satisfies(e -> assertThat(((AiCallException) e).kind()).isEqualTo(Kind.UPSTREAM_ERROR))
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("INVALID_ARGUMENT Unknown name speech_config")
                .hasMessageNotContaining("test-key")
                .hasMessageNotContaining("Hello");
    }

    @Test
    void moreThanTwoSpeakersOrMissingVoiceIsRejectedWithoutCallingApi() {
        var three = List.of(new SpeechLine("A", "1"), new SpeechLine("B", "2"), new SpeechLine("C", "3"));

        assertThatThrownBy(() -> tts.synthesize(three, Map.of("A", "Kore", "B", "Puck", "C", "Leda"), null, 24000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most 2");
        assertThatThrownBy(() -> tts.synthesize(List.of(new SpeechLine("A", "1")), Map.of(), null, 24000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("voice");
        server.verify();
    }

    @Test
    void responseWithoutAudioOrFailedStatusIsUpstreamError() {
        server.expect(requestTo(BASE + "/interactions")).andRespond(withSuccess(
                "{\"id\":\"i\",\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/interactions")).andRespond(withSuccess(
                "{\"id\":\"i\",\"status\":\"failed\",\"steps\":[],\"errors\":[{\"code\":\"internal\",\"message\":\"x\"}]}",
                MediaType.APPLICATION_JSON));
        var lines = List.of(new SpeechLine("A", "Hello"));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> tts.synthesize(lines, Map.of("A", "Kore"), null, 24000))
                    .satisfies(e -> assertThat(((AiCallException) e).kind()).isEqualTo(Kind.UPSTREAM_ERROR));
        }
    }

    @Test
    void ttsModelNotConfiguredFailsClearly() {
        raw.put("ai.model.tts", "\"\"");

        assertThatThrownBy(() -> tts.synthesize(List.of(new SpeechLine("A", "Hello")), Map.of("A", "Kore"), null, 24000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ai.model.tts");
    }

    @Test
    void wavDurationHandlesExtraChunksAndOversizedDataHeader() throws Exception {
        byte[] base = wav(16000, 8000);
        ByteArrayOutputStream withList = new ByteArrayOutputStream();
        withList.write(base, 0, 36);                                  // RIFF + fmt
        withList.write("LIST".getBytes(StandardCharsets.US_ASCII));   // chunk lạ trước data
        withList.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(4).array());
        withList.write(new byte[] {1, 2, 3, 4});
        withList.write("data".getBytes(StandardCharsets.US_ASCII));
        withList.write(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}); // kích thước "vô hạn"
        withList.write(new byte[16000]);

        assertThat(WavInfo.duration(withList.toByteArray())).isEqualTo(Duration.ofMillis(500));
        assertThatThrownBy(() -> WavInfo.duration("not a wav file".getBytes())).isInstanceOf(IllegalArgumentException.class);
    }
}
