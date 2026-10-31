package com.pela.platform.adapter.out.gemini;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kết nối Gemini API. Tên model, ngưỡng an toàn… không nằm ở đây mà ở system_setting (MNT-3).
 *
 * @param apiKey         {@code GEMINI_API_KEY}; chỉ backend giữ
 * @param baseUrl        vd https://generativelanguage.googleapis.com/v1beta
 * @param connectTimeout thời gian mở kết nối
 * @param requestTimeout thời gian chờ tối đa cho embedding/TTS (LLM dùng timeout do use case truyền vào)
 */
@ConfigurationProperties(prefix = "pela.gemini")
public record GeminiProperties(String apiKey, String baseUrl, Duration connectTimeout, Duration requestTimeout) {

    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(60) : requestTimeout;
    }

    @Override
    public String toString() {
        return "GeminiProperties[baseUrl=" + baseUrl + "]";
    }
}
