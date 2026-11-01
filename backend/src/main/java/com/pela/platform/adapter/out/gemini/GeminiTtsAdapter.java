package com.pela.platform.adapter.out.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.TtsPort;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * TTS qua Gemini Interactions API ({@code POST /interactions}), theo trang chính thức "Speech generation":
 * mỗi lượt thoại là một content {@code type=text} kèm annotation {@code speech_metadata} (speaker, style);
 * giọng khai báo ở {@code generation_config.speech_config} (mảng {@code [{voice}]} khi 1 người nói, object
 * {@code {speakers:[{speaker, voice}]}} khi 2 người); tối đa 2 người nói mỗi lần gọi. Audio trả ở
 * {@code steps[type=model_output].content[type=audio].data} (base64, WAV khi gọi không stream).
 */
public class GeminiTtsAdapter implements TtsPort {

    static final String MODEL_SETTING = "ai.model.tts";
    static final int MAX_SPEAKERS = 2;

    /**
     * Hai cách đặt giọng trong {@code generation_config} đang chờ xác nhận bằng test external; xoá biến thể sai
     * (và property {@code pela.gemini.tts-variant}) sau khi có kết quả.
     */
    public enum Variant {
        /** Theo trang hướng dẫn: {@code speech_config} là mảng {@code [{voice}]} (1 người) hoặc {@code {speakers:[...]}} (2 người). */
        SPEECH_CONFIG,
        /** Theo tham chiếu API: {@code structured_speech_config: {speakers:[{speaker, voice}]}} cho mọi số người nói. */
        STRUCTURED_SPEECH_CONFIG
    }

    private final RestClient client;
    private final SettingsPort settings;
    private final Duration timeout;
    private final Variant variant;
    private final ObjectMapper json = new ObjectMapper();

    public GeminiTtsAdapter(RestClient client, SettingsPort settings, Duration timeout) {
        this(client, settings, timeout, Variant.SPEECH_CONFIG);
    }

    public GeminiTtsAdapter(RestClient client, SettingsPort settings, Duration timeout, Variant variant) {
        this.client = client;
        this.settings = settings;
        this.timeout = timeout;
        this.variant = variant;
    }

    @Override
    public SynthesizedAudio synthesize(List<SpeechLine> lines, Map<String, String> voices, String style, int sampleRate) {
        String model = settings.string(MODEL_SETTING).filter(m -> !m.isBlank())
                .orElseThrow(() -> new IllegalStateException("system_setting ai.model.tts is not configured"));
        ObjectNode body = requestBody(model, lines, voices, style, sampleRate);

        JsonNode response = GeminiHttp.call("tts", timeout, () -> client.post()
                .uri("/interactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        byte[] wav = audio(response);
        try {
            return new SynthesizedAudio(wav, WavInfo.duration(wav));
        } catch (IllegalArgumentException e) {
            throw GeminiHttp.failure("tts", Kind.UPSTREAM_ERROR, "unexpected audio format", e);
        }
    }

    ObjectNode requestBody(String model, List<SpeechLine> lines, Map<String, String> voices, String style, int sampleRate) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("No lines to synthesize");
        }
        Set<String> speakers = new LinkedHashSet<>();
        for (SpeechLine line : lines) {
            if (line.text() == null || line.text().isBlank()) {
                throw new IllegalArgumentException("Empty line text");
            }
            speakers.add(line.speaker());
        }
        if (speakers.size() > MAX_SPEAKERS) {
            throw new IllegalArgumentException("TTS supports at most " + MAX_SPEAKERS + " speakers per call");
        }
        for (String speaker : speakers) {
            if (voices.get(speaker) == null || voices.get(speaker).isBlank()) {
                throw new IllegalArgumentException("No voice configured for speaker " + speaker);
            }
        }
        boolean multiSpeaker = speakers.size() > 1;

        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        ObjectNode input = body.putArray("input").addObject();
        input.put("type", "user_input");
        ArrayNode content = input.putArray("content");
        for (SpeechLine line : lines) {
            ObjectNode item = content.addObject();
            item.put("type", "text");
            item.put("text", line.text());
            ObjectNode metadata = json.createObjectNode().put("type", "speech_metadata");
            if (multiSpeaker) {
                metadata.put("speaker", line.speaker());
            }
            if (style != null && !style.isBlank()) {
                metadata.put("style", style);
            }
            if (metadata.size() > 1) {
                item.putArray("annotations").add(metadata);
            }
        }

        ObjectNode format = body.putObject("response_format");
        format.put("type", "audio");
        format.put("mime_type", "audio/wav");
        format.put("sample_rate", sampleRate);

        ObjectNode generationConfig = body.putObject("generation_config");
        if (variant == Variant.STRUCTURED_SPEECH_CONFIG) {
            ArrayNode speakerConfigs = generationConfig.putObject("structured_speech_config").putArray("speakers");
            for (String speaker : speakers) {
                speakerConfigs.addObject().put("speaker", speaker).put("voice", voices.get(speaker));
            }
        } else if (multiSpeaker) {
            ArrayNode speakerConfigs = generationConfig.putObject("speech_config").putArray("speakers");
            for (String speaker : speakers) {
                speakerConfigs.addObject().put("speaker", speaker).put("voice", voices.get(speaker));
            }
        } else {
            generationConfig.putArray("speech_config").addObject().put("voice", voices.get(speakers.iterator().next()));
        }
        return body;
    }

    static byte[] audio(JsonNode response) {
        if (response == null) {
            throw GeminiHttp.failure("tts", Kind.UPSTREAM_ERROR, "empty response", null);
        }
        if (response.path("errors").isArray() && !response.path("errors").isEmpty()) {
            throw GeminiHttp.failure("tts", Kind.UPSTREAM_ERROR,
                    "errors: " + response.path("errors").path(0).path("code").asText(), null);
        }
        String status = response.path("status").asText("");
        if (status.equals("failed") || status.equals("cancelled")) {
            throw GeminiHttp.failure("tts", Kind.UPSTREAM_ERROR, "status=" + status, null);
        }
        String data = null;
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asText())) {
                continue;
            }
            for (JsonNode item : step.path("content")) {
                if ("audio".equals(item.path("type").asText()) && item.hasNonNull("data")) {
                    data = item.get("data").asText();
                }
            }
        }
        if (data == null) {
            throw GeminiHttp.failure("tts", Kind.UPSTREAM_ERROR, "no audio in response (status=" + status + ")", null);
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw GeminiHttp.failure("tts", Kind.UPSTREAM_ERROR, "audio is not base64", e);
        }
    }
}
