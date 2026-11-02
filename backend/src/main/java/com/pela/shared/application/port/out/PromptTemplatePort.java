package com.pela.shared.application.port.out;

import com.pela.shared.domain.text.TemplateRenderer;
import java.util.Map;
import java.util.Optional;

/** Prompt AI có version trong bảng {@code prompt_template} (MNT-4); không viết prompt trong code. Cache 5 phút. */
public interface PromptTemplatePort {

    /** Bản đang active của {@code name} (mỗi name tối đa một bản active). */
    Optional<PromptTemplate> active(String name);

    /**
     * @param responseSchema JSON Schema (chuỗi JSON) bắt buộc cho output; null nếu template không định nghĩa
     * @param temperature    null = mặc định của model
     */
    record PromptTemplate(String name, int version, String systemText, String body, String responseSchema,
            Double temperature) {

        /** Thay {@code {{x}}} trong system_text và body; thiếu biến → MissingTemplateVariableException. */
        public RenderedPrompt render(Map<String, String> vars) {
            return new RenderedPrompt(name, version, TemplateRenderer.render(systemText, vars),
                    TemplateRenderer.render(body, vars), responseSchema, temperature);
        }
    }

    record RenderedPrompt(String name, int version, String systemText, String userText, String responseSchema,
            Double temperature) {

        @Override
        public String toString() {
            // Không log nội dung prompt (có thể chứa bài viết của learner)
            return "RenderedPrompt[" + name + " v" + version + "]";
        }
    }
}
