package com.pela.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.shared.application.port.out.ContentFilterPort;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.PromptTemplatePort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.application.port.out.TtsPort;
import com.pela.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Cấu hình AI đọc từ system_setting / prompt_template thật (seed V3) và context nối đủ các port. */
class AiConfigAdaptersIT extends PostgresIntegrationTest {

    @Autowired
    SettingsPort settings;

    @Autowired
    PromptTemplatePort prompts;

    @Autowired
    LlmPort llm;

    @Autowired
    EmbeddingPort embedding;

    @Autowired
    TtsPort tts;

    @Autowired
    ContentFilterPort contentFilter;

    @Test
    void readsTypedSettingsSeededByMigrations() {
        assertThat(settings.requiredString("ai.model.text")).isEqualTo("gemini-3.8-flash");
        assertThat(settings.requiredString("ai.model.embedding")).isEqualTo("gemini-embedding-001");
        assertThat(settings.requiredInt("ai.embedding.dimensions")).isEqualTo(768);
        assertThat(settings.requiredDouble("rag.retrieval.min_similarity")).isEqualTo(0.65);
        assertThat(settings.requiredString("ai.safety.block_threshold")).isEqualTo("BLOCK_LOW_AND_ABOVE");
        assertThat(settings.requiredJson("ai.tts.style_by_level", Map.class)).containsEntry("A1", "Speak slowly and clearly");
        assertThat(settings.string("ai.model.tts")).contains("gemini-3.8-flash-tts");
    }

    @Test
    void readsActivePromptTemplate() {
        var template = prompts.active("writing_grading").orElseThrow();

        assertThat(template.version()).isEqualTo(3);
        assertThat(template.body()).contains("{{numbered_sentences}}", "{{repeated_words}}");
        assertThat(template.systemText()).contains("{{level}}");
        assertThat(template.responseSchema()).contains("\"vocab_candidates\"", "\"cefr_level\"");
        assertThat(template.temperature()).isEqualTo(0.2);
        assertThat(prompts.active("no_such_template")).isEmpty();
    }

    @Test
    void allAiPortsAreWired() {
        assertThat(llm).isNotNull();
        assertThat(embedding).isNotNull();
        assertThat(tts).isNotNull();
        assertThat(contentFilter.check("A normal sentence about class.").allowed()).isTrue();
    }
}
