package com.pela.platform.adapter.out.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.PromptTemplatePort;
import com.pela.shared.application.port.out.PromptTemplatePort.PromptTemplate;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.text.MissingTemplateVariableException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiLlmAdapterTest {

    private static final String BASE = "https://gemini.test/v1beta";
    private static final String URL = BASE + "/models/gemini-2.5-flash:generateContent";
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"score\":{\"type\":\"integer\"},"
            + "\"comment\":{\"type\":\"string\"}},\"required\":[\"score\",\"comment\"]}";

    /** Kiểu output mẫu như một use case chấm Writing sẽ dùng. */
    record Grade(@NotNull @Min(0) @Max(100) Integer score, @NotBlank String comment) {
    }

    private final SettingsPort settings = key -> Optional.ofNullable(Map.of(
            "ai.model.text", "\"gemini-2.5-flash\"",
            "ai.safety.block_threshold", "\"BLOCK_LOW_AND_ABOVE\"").get(key));
    private final PromptTemplatePort prompts = name -> Optional.of(new PromptTemplate(name, 7,
            "You are an English teacher for {{level}} learners.", "Grade this essay:\n{{essay}}", SCHEMA, 0.3));

    private MockRestServiceServer server;
    private GeminiLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("x-goog-api-key", "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GeminiLlmAdapter(builder.build(), prompts, settings,
                Validation.buildDefaultValidatorFactory().getValidator(), new ObjectMapper());
    }

    private static String candidateText(String text, String finishReason) {
        return """
                {"candidates":[{"content":{"role":"model","parts":[{"text":%s}]},"finishReason":"%s"}]}
                """.formatted(new ObjectMapper().valueToTree(text).toString(), finishReason);
    }

    private Grade call() {
        return adapter.generateJson("writing_grading", Map.of("level", "B1", "essay", "My essay text"), Grade.class,
                Duration.ofSeconds(2)).value();
    }

    @Test
    void sendsStructuredOutputRequestBuiltFromTemplateAndSettings() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("You are an English teacher for B1 learners."))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Grade this essay:\nMy essay text"))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0.3))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.mimeType").value("APPLICATION_JSON"))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.required[0]").value("score"))
                .andExpect(jsonPath("$.safetySettings.length()").value(4))
                .andExpect(jsonPath("$.safetySettings[*].threshold", Matchers.everyItem(Matchers.is("BLOCK_LOW_AND_ABOVE"))))
                .andExpect(jsonPath("$.safetySettings[*].category", Matchers.containsInAnyOrder(
                        "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")))
                .andRespond(withSuccess(candidateText("{\"score\":78,\"comment\":\"Good structure\"}", "STOP"),
                        MediaType.APPLICATION_JSON));

        var result = adapter.generateJson("writing_grading", Map.of("level", "B1", "essay", "My essay text"),
                Grade.class, Duration.ofSeconds(2));

        assertThat(result.value()).isEqualTo(new Grade(78, "Good structure"));
        assertThat(result.templateVersion()).isEqualTo(7);
        assertThat(result.model()).isEqualTo("gemini-2.5-flash");
        server.verify();
    }

    @Test
    void ignoresThoughtPartsAndJoinsAnswerParts() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[
                  {"text":"thinking...","thought":true},
                  {"text":"{\\"score\\":50,"},{"text":"\\"comment\\":\\"ok\\"}"}
                ]},"finishReason":"STOP"}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(call()).isEqualTo(new Grade(50, "ok"));
    }

    @Test
    void missingTemplateVariableFailsBeforeCallingGemini() {
        assertThatThrownBy(() -> adapter.generateJson("writing_grading", Map.of("level", "B1"), Grade.class,
                Duration.ofSeconds(1)))
                .isInstanceOf(MissingTemplateVariableException.class);
        server.verify();
    }

    @Test
    void notJsonIsInvalidJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess(candidateText("Sure! Here is the grade: 78", "STOP"),
                MediaType.APPLICATION_JSON));

        assertKind(Kind.INVALID_JSON);
    }

    @Test
    void jsonFailingBeanValidationIsInvalidJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess(candidateText("{\"score\":140,\"comment\":\"\"}", "STOP"),
                MediaType.APPLICATION_JSON));

        assertKind(Kind.INVALID_JSON);
    }

    @Test
    void missingRequiredFieldIsInvalidJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess(candidateText("{\"comment\":\"no score\"}", "STOP"),
                MediaType.APPLICATION_JSON));

        assertKind(Kind.INVALID_JSON);
    }

    @Test
    void truncatedOutputIsInvalidJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess(candidateText("{\"score\":7", "MAX_TOKENS"),
                MediaType.APPLICATION_JSON));

        assertKind(Kind.INVALID_JSON);
    }

    @Test
    void safetyFinishReasonIsSafetyBlocked() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"safetyRatings\":[]}]}", MediaType.APPLICATION_JSON));

        assertKind(Kind.SAFETY_BLOCKED);
    }

    @Test
    void blockedPromptIsSafetyBlocked() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\",\"safetyRatings\":[]}}", MediaType.APPLICATION_JSON));

        assertKind(Kind.SAFETY_BLOCKED);
    }

    @Test
    void http429IsRateLimited() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        assertKind(Kind.RATE_LIMITED);
    }

    @Test
    void http5xxIsUpstreamError() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertKind(Kind.UPSTREAM_ERROR);
    }

    @Test
    void socketTimeoutIsTimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertKind(Kind.TIMEOUT);
    }

    @Test
    void slowResponseBeyondCallerTimeoutIsTimeout() {
        server.expect(requestTo(URL)).andRespond(request -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            return new MockClientHttpResponse(candidateText("{\"score\":1,\"comment\":\"x\"}", "STOP").getBytes(), HttpStatus.OK);
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.generateJson("writing_grading", Map.of("level", "B1", "essay", "e"),
                Grade.class, Duration.ofMillis(200)))
                .isInstanceOf(AiCallException.class)
                .satisfies(e -> assertThat(((AiCallException) e).kind()).isEqualTo(Kind.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void errorMessagesDoNotContainLearnerText() {
        server.expect(requestTo(URL)).andRespond(withSuccess(candidateText("not json", "STOP"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::call).satisfies(e -> assertThat(e.getMessage()).doesNotContain("My essay text", "not json"));
    }

    @Test
    void requestBodyContainsNoApiKey() {
        server.expect(requestTo(URL))
                .andExpect(content().string(Matchers.not(Matchers.containsString("test-key"))))
                .andRespond(withSuccess(candidateText("{\"score\":1,\"comment\":\"x\"}", "STOP"), MediaType.APPLICATION_JSON));

        call();
        server.verify();
    }

    private void assertKind(Kind expected) {
        assertThatThrownBy(this::call)
                .isInstanceOf(AiCallException.class)
                .satisfies(e -> assertThat(((AiCallException) e).kind()).isEqualTo(expected));
    }
}
