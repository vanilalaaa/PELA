package com.pela.rag.domain;

import java.util.Locale;
import java.util.Optional;

/** Dạng bài Writing (question_bank.question_type của đề writing_prompt). */
public enum WritingTaskType {
    EMAIL("email"),
    PARAGRAPH("paragraph"),
    ESSAY("essay");

    private final String dbValue;

    WritingTaskType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** Không phân biệt hoa thường; rỗng nếu không phải dạng bài hợp lệ. */
    public static Optional<WritingTaskType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (WritingTaskType type : values()) {
            if (type.dbValue.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
