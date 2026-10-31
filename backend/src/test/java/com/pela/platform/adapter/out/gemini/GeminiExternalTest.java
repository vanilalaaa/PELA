package com.pela.platform.adapter.out.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.PromptTemplatePort;
import com.pela.shared.application.port.out.PromptTemplatePort.PromptTemplate;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.TtsPort.SpeechLine;
import com.pela.shared.domain.vector.VectorMath;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gọi Gemini thật — KHÔNG chạy trong {@code ./gradlew build}. Chạy: {@code ./gradlew externalTest}
 * (xem backend/README.md). Key lấy từ biến môi trường GEMINI_API_KEY, nếu không có thì từ backend/.env;
 * không bao giờ in key. Model lấy từ GEMINI_TEXT_MODEL / GEMINI_EMBEDDING_MODEL / GEMINI_TTS_MODEL, mặc
 * định theo seed V3 (TTS bắt buộc truyền vì ai.model.tts chưa chọn).
 */
@Tag("external")
class GeminiExternalTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private static RestClient client;
    private static SettingsPort settings;

    record Answer(@NotBlank String word, @NotBlank String meaningVi) {
    }

    @BeforeAll
    static void setUp() throws IOException {
        String apiKey = config("GEMINI_API_KEY", null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set (environment or backend/.env)");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        factory.setReadTimeout(Duration.ofSeconds(90));
        client = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).defaultHeader("x-goog-api-key", apiKey).build();
        Map<String, String> raw = Map.of(
                "ai.model.text", quote(config("GEMINI_TEXT_MODEL", "gemini-2.5-flash")),
                "ai.model.embedding", quote(config("GEMINI_EMBEDDING_MODEL", "gemini-embedding-001")),
                "ai.embedding.dimensions", "768",
                "ai.model.tts", quote(config("GEMINI_TTS_MODEL", "")),
                "ai.safety.block_threshold", quote("BLOCK_LOW_AND_ABOVE"));
        settings = key -> Optional.ofNullable(raw.get(key));
    }

    @Test
    void generatesValidatedStructuredJson() {
        PromptTemplatePort prompts = name -> Optional.of(new PromptTemplate(name, 1,
                "You are a dictionary for {{level}} learners. Answer only with JSON.",
                "Give the English word for: {{concept}}. Include its Vietnamese meaning.",
                """
                {"type":"object","properties":{"word":{"type":"string"},"meaningVi":{"type":"string"}},
                 "required":["word","meaningVi"]}
                """, 0.0));
        var adapter = new GeminiLlmAdapter(client, prompts, settings, Validation.buildDefaultValidatorFactory().getValidator(),
                new ObjectMapper());

        var result = adapter.generateJson("external_test", Map.of("level", "A2", "concept", "a document for travelling abroad"),
                Answer.class, Duration.ofSeconds(60));

        assertThat(result.value().word()).isNotBlank();
        assertThat(result.value().meaningVi()).isNotBlank();
        assertThat(result.templateVersion()).isEqualTo(1);
    }

    @Test
    void embedsAndNormalizes768Dimensions() {
        var adapter = new GeminiEmbeddingAdapter(client, settings, Duration.ofSeconds(60));

        float[] document = adapter.embed("Write an email to a friend about your last holiday.", TaskType.RETRIEVAL_DOCUMENT);
        float[] query = adapter.embed("holiday email writing prompt", TaskType.RETRIEVAL_QUERY);

        assertThat(document).hasSize(768);
        assertThat(VectorMath.l2Norm(document)).isCloseTo(1.0, within(1e-4));
        assertThat(VectorMath.l2Norm(query)).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void synthesizesTwoSpeakerDialogue() throws IOException {
        Assumptions.assumeTrue(!settings.requiredString("ai.model.tts").isBlank(),
                "Set GEMINI_TTS_MODEL to run the TTS check");
        var adapter = new GeminiTtsAdapter(client, settings, Duration.ofSeconds(90));

        var audio = adapter.synthesize(
                List.of(new SpeechLine("Agent", "Good morning. Can I see your passport, please?"),
                        new SpeechLine("Traveller", "Sure, here you are.")),
                Map.of("Agent", "Kore", "Traveller", "Puck"), "Speak slowly and clearly", 24000);

        assertThat(audio.duration()).isGreaterThan(Duration.ofSeconds(1));
        Path out = Path.of("build", "external-test-tts.wav");
        Files.createDirectories(out.getParent());
        Files.write(out, audio.wav());
    }

    private static String config(String name, String fallback) throws IOException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            Path env = Path.of(".env");
            if (Files.exists(env)) {
                Properties properties = new Properties();
                try (InputStream in = Files.newInputStream(env)) {
                    properties.load(in);
                }
                value = properties.getProperty(name);
            }
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String quote(String value) {
        return new ObjectMapper().valueToTree(value).toString();
    }
}
