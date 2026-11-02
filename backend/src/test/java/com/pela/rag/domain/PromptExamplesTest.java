package com.pela.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PromptExamplesTest {

    private static final PromptExample EMAIL = new PromptExample("Write an email to a friend.", "email", 20, 40);
    private static final PromptExample PARAGRAPH = new PromptExample(" Describe your town. ", "paragraph", 30, 60);
    private static final PromptExample NO_META = new PromptExample("Tell a story.", null, null, null);

    @Test
    void rendersNumberedLinesWithTypeAndRange() {
        assertThat(PromptExamples.render(List.of(EMAIL, PARAGRAPH, NO_META))).isEqualTo("""
                1. [email, 20-40 words] Write an email to a friend.
                2. [paragraph, 30-60 words] Describe your town.
                3. [unknown type] Tell a story.""");
    }

    @Test
    void taskTypeRuleFollowsTheRequestedTypeOrTheFirstExample() {
        assertThat(PromptExamples.taskTypeRule(Optional.of(WritingTaskType.ESSAY)))
                .isEqualTo("the task type must be essay");
        assertThat(PromptExamples.taskTypeRule(Optional.empty())).isEqualTo("keep the task type of the first example");
    }

    @Test
    void allowedTypesAreTheRequestedTypeOrTheTypesOfTheExamples() {
        assertThat(PromptExamples.allowedTypes(Optional.of(WritingTaskType.ESSAY), List.of(EMAIL)))
                .containsExactly(WritingTaskType.ESSAY);
        assertThat(PromptExamples.allowedTypes(Optional.empty(), List.of(EMAIL, PARAGRAPH, NO_META)))
                .containsExactlyInAnyOrder(WritingTaskType.EMAIL, WritingTaskType.PARAGRAPH);
        assertThat(PromptExamples.allowedTypes(Optional.empty(), List.of(NO_META))).isEmpty();
    }

    @Test
    void wordRangeCoversExamplesThatHaveOne() {
        assertThat(PromptExamples.wordRange(List.of(EMAIL, PARAGRAPH, NO_META)))
                .contains(new PromptExamples.WordRange(20, 60));
        assertThat(PromptExamples.wordRange(List.of(NO_META))).isEmpty();
        assertThat(PromptExamples.wordRange(List.of())).isEmpty();
    }
}
