package com.pela.shared.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Đọc cấu hình bảng {@code system_setting} (MNT-3): ngưỡng, giới hạn, tên model… không hard-code.
 * Adapter cache kết quả 5 phút, nên đổi setting có hiệu lực chậm nhất sau khoảng đó.
 */
public interface SettingsPort {

    /** Giá trị cột value (jsonb) dạng chuỗi JSON thô, vd {@code 5}, {@code "gemini-2.5-flash"}, {@code {...}}. */
    Optional<String> rawJson(String key);

    /** Chuỗi: JSON string được bỏ dấu nháy; số/boolean trả dạng chữ. */
    default Optional<String> string(String key) {
        return node(key).map(n -> n.isTextual() ? n.asText() : n.toString());
    }

    default OptionalInt intValue(String key) {
        Optional<JsonNode> node = node(key);
        if (node.isEmpty()) {
            return OptionalInt.empty();
        }
        JsonNode n = node.get();
        return OptionalInt.of(n.isTextual() ? Integer.parseInt(n.asText().trim()) : n.intValue());
    }

    default OptionalDouble doubleValue(String key) {
        Optional<JsonNode> node = node(key);
        if (node.isEmpty()) {
            return OptionalDouble.empty();
        }
        JsonNode n = node.get();
        return OptionalDouble.of(n.isTextual() ? Double.parseDouble(n.asText().trim()) : n.doubleValue());
    }

    /** JSON object/array map vào kiểu {@code type} (record, Map, List…). */
    default <T> Optional<T> json(String key, Class<T> type) {
        return node(key).map(n -> {
            try {
                return SettingJson.MAPPER.treeToValue(n, type);
            } catch (Exception e) {
                throw new IllegalStateException("system_setting " + key + " is not a valid " + type.getSimpleName(), e);
            }
        });
    }

    default String requiredString(String key) {
        return string(key).orElseThrow(() -> missing(key));
    }

    default int requiredInt(String key) {
        return intValue(key).orElseThrow(() -> missing(key));
    }

    default double requiredDouble(String key) {
        return doubleValue(key).orElseThrow(() -> missing(key));
    }

    default <T> T requiredJson(String key, Class<T> type) {
        return json(key, type).orElseThrow(() -> missing(key));
    }

    private Optional<JsonNode> node(String key) {
        return rawJson(key).map(raw -> {
            try {
                return SettingJson.MAPPER.readTree(raw);
            } catch (Exception e) {
                throw new IllegalStateException("system_setting " + key + " is not valid JSON", e);
            }
        });
    }

    private static IllegalStateException missing(String key) {
        return new IllegalStateException("Missing system_setting: " + key);
    }

    /** ObjectMapper riêng cho giá trị setting (không phụ thuộc cấu hình Jackson của web). */
    final class SettingJson {
        static final ObjectMapper MAPPER = new ObjectMapper();

        private SettingJson() {
        }
    }
}
