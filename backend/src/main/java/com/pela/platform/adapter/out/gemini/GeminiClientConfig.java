package com.pela.platform.adapter.out.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.shared.application.port.out.PromptTemplatePort;
import com.pela.shared.application.port.out.SettingsPort;
import jakarta.validation.Validator;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Nối các adapter Gemini; API key chỉ gửi qua header {@code x-goog-api-key}, không nằm trong URL. */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
class GeminiClientConfig {

    @Bean
    RestClient geminiRestClient(RestClient.Builder builder, GeminiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .build();
    }

    @Bean
    GeminiLlmAdapter geminiLlmAdapter(RestClient geminiRestClient, PromptTemplatePort prompts, SettingsPort settings,
            Validator validator, ObjectMapper objectMapper) {
        return new GeminiLlmAdapter(geminiRestClient, prompts, settings, validator, objectMapper);
    }

    @Bean
    GeminiEmbeddingAdapter geminiEmbeddingAdapter(RestClient geminiRestClient, SettingsPort settings,
            GeminiProperties properties) {
        return new GeminiEmbeddingAdapter(geminiRestClient, settings, properties.requestTimeout());
    }

    @Bean
    GeminiTtsAdapter geminiTtsAdapter(RestClient geminiRestClient, SettingsPort settings, GeminiProperties properties) {
        return new GeminiTtsAdapter(geminiRestClient, settings, properties.requestTimeout());
    }
}
