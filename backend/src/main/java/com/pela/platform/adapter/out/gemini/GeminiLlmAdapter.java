package com.pela.platform.adapter.out.gemini;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.PromptTemplatePort;
import com.pela.shared.application.port.out.PromptTemplatePort.PromptTemplate;
import com.pela.shared.application.port.out.PromptTemplatePort.RenderedPrompt;
import com.pela.shared.application.port.out.SettingsPort;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * LLM qua Gemini {@code models/{model}:generateContent} (REST, theo Gemini API reference và trang
 * "Structured outputs" — JSON schema ở {@code generationConfig.responseFormat.text}). Model đọc từ
 * {@code ai.model.text}, ngưỡng an toàn từ {@code ai.safety.block_threshold}, prompt/schema/temperature từ
 * prompt_template.
 */
public class GeminiLlmAdapter implements LlmPort {

    static final String MODEL_SETTING = "ai.model.text";
    static final String SAFETY_THRESHOLD_SETTING = "ai.safety.block_threshold";

    /** Bốn nhóm nội dung có hại của Gemini được áp ngưỡng chặn (BR-15). */
    static final List<String> SAFETY_CATEGORIES = List.of(
            "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT");

    /** finishReason cho biết output bị chặn vì an toàn/chính sách. */
    private static final Set<String> BLOCKING_FINISH_REASONS = Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII");

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmAdapter.class);

    private final RestClient client;
    private final PromptTemplatePort prompts;
    private final SettingsPort settings;
    private final Validator validator;
    private final ObjectMapper json;

    public GeminiLlmAdapter(RestClient client, PromptTemplatePort prompts, SettingsPort settings, Validator validator,
            ObjectMapper objectMapper) {
        this.client = client;
        this.prompts = prompts;
        this.settings = settings;
        this.validator = validator;
        this.json = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
    }

    @Override
    public <T> LlmResult<T> generateJson(String templateName, Map<String, String> vars, Class<T> responseType,
            Duration timeout) {
        PromptTemplate template = prompts.active(templateName)
                .orElseThrow(() -> new IllegalStateException("No active prompt_template: " + templateName));
        RenderedPrompt prompt = template.render(vars);
        String model = settings.requiredString(MODEL_SETTING);
        ObjectNode body = requestBody(prompt, settings.requiredString(SAFETY_THRESHOLD_SETTING));

        JsonNode response = GeminiHttp.call("generateContent", timeout, () -> client.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        String text = outputText(response);
        T value = parse(text, responseType);
        log.info("Gemini generateContent ok: template={} v{}, model={}", templateName, template.version(), model);
        return new LlmResult<>(value, template.version(), model);
    }

    ObjectNode requestBody(RenderedPrompt prompt, String safetyThreshold) {
        ObjectNode body = json.createObjectNode();
        if (prompt.systemText() != null && !prompt.systemText().isBlank()) {
            body.putObject("systemInstruction").putArray("parts").addObject().put("text", prompt.systemText());
        }
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", prompt.userText());

        ObjectNode generationConfig = body.putObject("generationConfig");
        if (prompt.temperature() != null) {
            generationConfig.put("temperature", prompt.temperature());
        }
        if (prompt.thinkingLevel() != null && !prompt.thinkingLevel().isBlank()) {
            // Mức suy nghĩ cấu hình theo prompt_template.thinking_level; không gửi thì model dùng mặc định
            generationConfig.putObject("thinkingConfig").put("thinkingLevel", prompt.thinkingLevel());
        }
        ObjectNode textFormat = generationConfig.putObject("responseFormat").putObject("text");
        textFormat.put("mimeType", "APPLICATION_JSON");
        if (prompt.responseSchema() != null && !prompt.responseSchema().isBlank()) {
            try {
                textFormat.set("schema", json.readTree(prompt.responseSchema()));
            } catch (Exception e) {
                throw new IllegalStateException("prompt_template " + prompt.name() + " has invalid response_schema", e);
            }
        }

        ArrayNode safety = body.putArray("safetySettings");
        for (String category : SAFETY_CATEGORIES) {
            safety.addObject().put("category", category).put("threshold", safetyThreshold);
        }
        return body;
    }

    /** Lấy text của candidate đầu; chặn an toàn → SAFETY_BLOCKED. */
    static String outputText(JsonNode response) {
        if (response == null) {
            throw GeminiHttp.failure("generateContent", Kind.UPSTREAM_ERROR, "empty response", null);
        }
        if (response.path("promptFeedback").hasNonNull("blockReason")) {
            throw GeminiHttp.failure("generateContent", Kind.SAFETY_BLOCKED,
                    "prompt blocked: " + response.path("promptFeedback").path("blockReason").asText(), null);
        }
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            throw GeminiHttp.failure("generateContent", Kind.UPSTREAM_ERROR, "no candidates", null);
        }
        String finishReason = candidate.path("finishReason").asText("");
        if (BLOCKING_FINISH_REASONS.contains(finishReason)) {
            throw GeminiHttp.failure("generateContent", Kind.SAFETY_BLOCKED, "finishReason=" + finishReason, null);
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            throw GeminiHttp.failure("generateContent", Kind.INVALID_JSON, "output truncated (MAX_TOKENS)", null);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            // Bỏ phần "suy nghĩ" của model thinking, chỉ lấy câu trả lời
            if (!part.path("thought").asBoolean(false) && part.hasNonNull("text")) {
                text.append(part.get("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw GeminiHttp.failure("generateContent", Kind.INVALID_JSON, "candidate has no text", null);
        }
        return text.toString();
    }

    private <T> T parse(String text, Class<T> responseType) {
        T value;
        try {
            value = json.readValue(text, responseType);
        } catch (Exception e) {
            throw GeminiHttp.failure("generateContent", Kind.INVALID_JSON, "unparseable output", e);
        }
        if (value == null) {
            throw GeminiHttp.failure("generateContent", Kind.INVALID_JSON, "null output", null);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw GeminiHttp.failure("generateContent", Kind.INVALID_JSON,
                    violations.size() + " constraint violation(s)", null);
        }
        return value;
    }
}
