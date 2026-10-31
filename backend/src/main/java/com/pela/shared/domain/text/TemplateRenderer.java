package com.pela.shared.domain.text;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Thay placeholder dạng {@code {{name}}} trong prompt (MNT-4). Thiếu biến nào là lỗi, không để trống. */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*\\}\\}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        Set<String> missing = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = vars.get(name);
            if (value == null) {
                missing.add(name);
                matcher.appendReplacement(out, "");
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(out);
        if (!missing.isEmpty()) {
            throw new MissingTemplateVariableException(missing);
        }
        return out.toString();
    }

    /** Tên mọi placeholder xuất hiện trong template. */
    public static Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (template != null) {
            Matcher matcher = PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }
}
