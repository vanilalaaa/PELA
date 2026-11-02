package com.pela.shared.domain.text;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Template có placeholder chưa được truyền giá trị — lỗi lập trình/cấu hình, không phải lỗi người dùng. */
public class MissingTemplateVariableException extends IllegalArgumentException {

    private final transient Set<String> missing;

    public MissingTemplateVariableException(Set<String> missing) {
        super("Missing template variables: " + missing);
        // Giữ thứ tự xuất hiện trong template để thông báo dễ đọc
        this.missing = Collections.unmodifiableSet(new LinkedHashSet<>(missing));
    }

    public Set<String> missing() {
        return missing;
    }
}
