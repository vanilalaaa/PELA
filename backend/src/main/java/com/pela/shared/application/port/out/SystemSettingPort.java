package com.pela.shared.application.port.out;

import java.util.Optional;
import java.util.OptionalInt;

/** Đọc cấu hình trong bảng {@code system_setting} (MNT-3): ngưỡng, giới hạn, tên model… không hard-code. */
public interface SystemSettingPort {

    /** Giá trị dạng chuỗi của setting (số, chuỗi JSON được bỏ dấu nháy). Rỗng nếu chưa có. */
    Optional<String> text(String key);

    default OptionalInt intValue(String key) {
        return text(key).map(v -> OptionalInt.of(Integer.parseInt(v.trim()))).orElse(OptionalInt.empty());
    }

    /** Setting bắt buộc phải có (migration tạo sẵn); thiếu là lỗi cấu hình. */
    default int requiredInt(String key) {
        return intValue(key).orElseThrow(() -> new IllegalStateException("Missing system_setting: " + key));
    }
}
