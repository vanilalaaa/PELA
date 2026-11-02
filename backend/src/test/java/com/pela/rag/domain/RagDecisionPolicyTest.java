package com.pela.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RagDecisionPolicyTest {

    private static final Set<WritingTaskType> EMAIL_ONLY = EnumSet.of(WritingTaskType.EMAIL);
    private static final Optional<PromptExamples.WordRange> RANGE = Optional.of(new PromptExamples.WordRange(20, 40));

    private static GeneratedPrompt prompt(String text, String type, int min, int max) {
        return new GeneratedPrompt(text, type, min, max);
    }

    @Test
    void thresholdIsInclusiveAndAnEmptyBankNeverPasses() {
        assertThat(RagDecisionPolicy.meetsRetrievalThreshold(Optional.of(0.65), 0.65)).isTrue();
        assertThat(RagDecisionPolicy.meetsRetrievalThreshold(Optional.of(0.6499), 0.65)).isFalse();
        assertThat(RagDecisionPolicy.meetsRetrievalThreshold(Optional.empty(), 0.65)).isFalse();
    }

    @Test
    void acceptsAValidPromptInsideTheExampleRange() {
        assertThat(RagDecisionPolicy.validate(prompt("Write an email about your trip.", "email", 20, 40), EMAIL_ONLY,
                RANGE)).isEmpty();
        assertThat(RagDecisionPolicy.validate(prompt("Write an email about your trip.", "Email", 25, 35), EMAIL_ONLY,
                RANGE)).isEmpty();
    }

    @Test
    void withoutConstraintsOnlyTheBusinessLimitsApply() {
        assertThat(RagDecisionPolicy.validate(prompt("Describe a day.", "essay", 20, 300), Set.of(), Optional.empty()))
                .isEmpty();
    }

    @Test
    void rejectsBlankOrTooLongText() {
        assertThat(RagDecisionPolicy.validate(prompt("  ", "email", 20, 40), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.validate(prompt(null, "email", 20, 40), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.validate(prompt("x".repeat(601), "email", 20, 40), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.validate(null, EMAIL_ONLY, RANGE)).contains(FallbackReason.INVALID_OUTPUT);
    }

    @Test
    void rejectsUnknownOrDisallowedTaskType() {
        assertThat(RagDecisionPolicy.validate(prompt("Write a poem.", "poem", 20, 40), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.validate(prompt("Describe a day.", "essay", 20, 40), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.validate(prompt("Describe a day.", null, 20, 40), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
    }

    @Test
    void rejectsInvalidWordRanges() {
        for (int[] range : new int[][] {{10, 40}, {40, 40}, {50, 40}, {20, 301}, {0, 0}}) {
            assertThat(RagDecisionPolicy.validate(prompt("Write an email.", "email", range[0], range[1]), EMAIL_ONLY,
                    Optional.empty())).as("range %d-%d", range[0], range[1]).contains(FallbackReason.INVALID_OUTPUT);
        }
    }

    @Test
    void rejectsRangeOutsideTheExamples() {
        assertThat(RagDecisionPolicy.validate(prompt("Write an email.", "email", 20, 60), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.validate(prompt("Write an email.", "email", 21, 41), EMAIL_ONLY, RANGE))
                .contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.validate(prompt("Write an email.", "email", 25, 30), EMAIL_ONLY, RANGE)).isEmpty();
    }

    @Test
    void bankSimilarityAtOrAboveTheLimitIsRejected() {
        assertThat(RagDecisionPolicy.checkBankSimilarity(Optional.of(0.95), 0.95)).contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.checkBankSimilarity(Optional.of(0.97), 0.95)).contains(FallbackReason.INVALID_OUTPUT);
        assertThat(RagDecisionPolicy.checkBankSimilarity(Optional.of(0.949), 0.95)).isEmpty();
        assertThat(RagDecisionPolicy.checkBankSimilarity(Optional.empty(), 0.95)).isEmpty();
    }
}
