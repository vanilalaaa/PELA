package com.pela.writing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CuratedPromptPolicyTest {

    private static final Instant T1 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-10-20T00:00:00Z");

    private static PromptCandidate candidate(int n, Instant lastDoneAt) {
        return new PromptCandidate(new UUID(0, n), "prompt " + n, "email", 20, 40, lastDoneAt);
    }

    @Test
    void picksARandomPromptTheLearnerHasNotDone() {
        List<PromptCandidate> all = List.of(candidate(1, T1), candidate(2, null), candidate(3, null), candidate(4, T2));

        assertThat(CuratedPromptPolicy.choose(all, n -> 0)).contains(candidate(2, null));
        assertThat(CuratedPromptPolicy.choose(all, n -> n - 1)).contains(candidate(3, null));
        // Chỉ chọn trong số đề chưa làm: chỉ số ngẫu nhiên nhận n = 2
        CuratedPromptPolicy.choose(all, n -> {
            assertThat(n).isEqualTo(2);
            return 0;
        });
    }

    @Test
    void whenEverythingIsDoneItTakesThePromptDoneLongestAgo() {
        List<PromptCandidate> all = List.of(candidate(1, T3), candidate(2, T1), candidate(3, T2));

        assertThat(CuratedPromptPolicy.choose(all, n -> {
            throw new AssertionError("no randomness needed when every prompt is done");
        })).contains(candidate(2, T1));
    }

    @Test
    void emptyBankGivesNothing() {
        assertThat(CuratedPromptPolicy.choose(List.of(), n -> 0)).isEmpty();
    }

    @Test
    void suggestionsListUndonePromptsFirstThenOldestDoneAndHonourTheLimit() {
        List<PromptCandidate> all = List.of(candidate(1, T3), candidate(2, null), candidate(3, T1), candidate(4, null),
                candidate(5, T2));

        assertThat(CuratedPromptPolicy.suggestions(all, 10)).extracting(PromptCandidate::questionId)
                .containsExactly(new UUID(0, 2), new UUID(0, 4), new UUID(0, 3), new UUID(0, 5), new UUID(0, 1));
        assertThat(CuratedPromptPolicy.suggestions(all, 3)).hasSize(3);
        assertThat(CuratedPromptPolicy.suggestions(all, 0)).isEmpty();
    }
}
