package com.pela.platform.adapter.out.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.vector.VectorMath;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Embedding qua Gemini {@code models/{model}:embedContent}. Ở số chiều khác 3072, Gemini không chuẩn hoá
 * vector nên adapter chuẩn hoá L2 trước khi trả.
 *
 * <p>{@code taskType} và {@code outputDimensionality} đặt ở cấp ngoài cùng của body. API reference ghi hai
 * trường này deprecated và khuyên dùng {@code embedContentConfig}, nhưng đã thử với API thật (01/11/2026,
 * gemini-embedding-001): {@code embedContentConfig} (camelCase lẫn snake_case) bị bỏ qua và trả 3072 chiều;
 * chỉ trường ở cấp ngoài trả đúng 768 chiều (norm ≈ 0.59, chưa chuẩn hoá). Kiểm lại khi đổi model.
 */
public class GeminiEmbeddingAdapter implements EmbeddingPort {

    static final String MODEL_SETTING = "ai.model.embedding";
    static final String DIMENSIONS_SETTING = "ai.embedding.dimensions";

    private final RestClient client;
    private final SettingsPort settings;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    public GeminiEmbeddingAdapter(RestClient client, SettingsPort settings, Duration timeout) {
        this.client = client;
        this.settings = settings;
        this.timeout = timeout;
    }

    @Override
    public float[] embed(String text, TaskType taskType) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed is empty");
        }
        String model = settings.requiredString(MODEL_SETTING);
        int dimensions = settings.requiredInt(DIMENSIONS_SETTING);

        ObjectNode body = json.createObjectNode();
        body.putObject("content").putArray("parts").addObject().put("text", text);
        body.put("taskType", taskType.name());
        body.put("outputDimensionality", dimensions);

        JsonNode response = GeminiHttp.call("embedContent", timeout, () -> client.post()
                .uri("/models/{model}:embedContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        JsonNode values = response == null ? null : response.path("embedding").path("values");
        if (values == null || !values.isArray() || values.size() != dimensions) {
            throw GeminiHttp.failure("embedContent", Kind.UPSTREAM_ERROR,
                    "expected " + dimensions + " values, got " + (values == null || !values.isArray() ? 0 : values.size()), null);
        }
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vector[i] = (float) values.get(i).asDouble();
        }
        try {
            return VectorMath.l2Normalize(vector);
        } catch (IllegalArgumentException e) {
            throw GeminiHttp.failure("embedContent", Kind.UPSTREAM_ERROR, e.getMessage(), e);
        }
    }
}
