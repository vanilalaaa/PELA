package com.pela.writing.domain;

/** Bậc CEFR theo thứ tự tăng dần. */
public enum CefrLevel {
    A1, A2, B1, B2, C1, C2;

    /** null hoặc giá trị lạ → null. */
    public static CefrLevel parse(String value) {
        if (value == null) {
            return null;
        }
        for (CefrLevel level : values()) {
            if (level.name().equalsIgnoreCase(value.trim())) {
                return level;
            }
        }
        return null;
    }
}
