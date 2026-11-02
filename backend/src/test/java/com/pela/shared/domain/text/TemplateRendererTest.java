package com.pela.shared.domain.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pela.shared.application.port.out.PromptTemplatePort.PromptTemplate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    @Test
    void replacesAllPlaceholdersIncludingRepeatsAndSpaces() {
        String out = TemplateRenderer.render("Level {{level}}, topic {{ topic }}. Again: {{level}}",
                Map.of("level", "B1", "topic", "Work"));

        assertThat(out).isEqualTo("Level B1, topic Work. Again: B1");
    }

    @Test
    void valuesAreInsertedLiterally() {
        // $ và \ trong bài viết của learner không được hiểu là ký hiệu regex
        String out = TemplateRenderer.render("Essay: {{essay}}", Map.of("essay", "Costs $5 \\ day {{x}}"));

        assertThat(out).isEqualTo("Essay: Costs $5 \\ day {{x}}");
    }

    @Test
    void missingVariablesAreReportedTogether() {
        assertThatThrownBy(() -> TemplateRenderer.render("{{a}} {{b}} {{c}}", Map.of("b", "x")))
                .isInstanceOf(MissingTemplateVariableException.class)
                .satisfies(e -> assertThat(((MissingTemplateVariableException) e).missing()).containsExactly("a", "c"));
    }

    @Test
    void extraVariablesAndTextWithoutPlaceholdersAreFine() {
        assertThat(TemplateRenderer.render("No placeholders", Map.of("unused", "x"))).isEqualTo("No placeholders");
        assertThat(TemplateRenderer.render(null, Map.of())).isNull();
    }

    @Test
    void listsPlaceholders() {
        assertThat(TemplateRenderer.placeholders("{{a}} {{ b.c }} {{a}}")).containsExactly("a", "b.c");
    }

    @Test
    void promptTemplateRendersSystemAndBody() {
        var template = new PromptTemplate("writing_grading", 3, "You grade {{level}} essays.", "Essay:\n{{essay}}",
                "{\"type\":\"object\"}", 0.2);

        var rendered = template.render(Map.of("level", "A2", "essay", "My trip"));

        assertThat(rendered.systemText()).isEqualTo("You grade A2 essays.");
        assertThat(rendered.userText()).isEqualTo("Essay:\nMy trip");
        assertThat(rendered.version()).isEqualTo(3);
        assertThat(rendered.toString()).doesNotContain("My trip");
        assertThatThrownBy(() -> template.render(Map.of("level", "A2"))).isInstanceOf(MissingTemplateVariableException.class);
    }
}
