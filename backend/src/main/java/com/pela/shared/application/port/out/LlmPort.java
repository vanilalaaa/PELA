package com.pela.shared.application.port.out;

import java.time.Duration;
import java.util.Map;

/**
 * Sinh JSON có cấu trúc từ prompt_template (MNT-4). Model, temperature, schema đọc từ cấu hình/template.
 * Lỗi ném dưới dạng {@link AiCallException} với Kind TIMEOUT, INVALID_JSON, SAFETY_BLOCKED, RATE_LIMITED,
 * UPSTREAM_ERROR. Output chỉ được trả khi đã parse và qua Bean Validation.
 */
public interface LlmPort {

    <T> LlmResult<T> generateJson(String templateName, Map<String, String> vars, Class<T> responseType, Duration timeout);

    /** @param templateVersion version prompt đã dùng (ghi vào writing_submission.prompt_version…) */
    record LlmResult<T>(T value, int templateVersion, String model) {
    }
}
