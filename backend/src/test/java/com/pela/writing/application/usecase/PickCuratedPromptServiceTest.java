package com.pela.writing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pela.writing.application.port.in.PickCuratedPromptUseCase.CuratedPrompt;
import com.pela.writing.application.port.out.CuratedPromptPort;
import com.pela.writing.domain.PromptCandidate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class PickCuratedPromptServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private final CuratedPromptPort port = mock(CuratedPromptPort.class);

    private PromptCandidate candidate(int n, Instant lastDoneAt) {
        return new PromptCandidate(new UUID(0, n), "prompt " + n, "email", 20, 40, lastDoneAt);
    }

    @Test
    void picksAnUndonePromptAndExposesItsFields() {
        when(port.candidates(USER, "Travel", "A2")).thenReturn(List.of(
                candidate(1, Instant.parse("2026-10-01T00:00:00Z")), candidate(2, null)));

        CuratedPrompt picked = new PickCuratedPromptService(port, RandomGenerator.getDefault())
                .pick(USER, "Travel", "A2").orElseThrow();

        assertThat(picked).isEqualTo(new CuratedPrompt(new UUID(0, 2), "prompt 2", "email", 20, 40, false));
    }

    @Test
    void emptyBankGivesNothing() {
        when(port.candidates(USER, "Travel", "A2")).thenReturn(List.of());

        assertThat(new PickCuratedPromptService(port, RandomGenerator.getDefault()).pick(USER, "Travel", "A2")).isEmpty();
    }

    @Test
    void suggestionsMarkWhichPromptsWereDone() {
        when(port.candidates(USER, "Travel", "A2")).thenReturn(List.of(
                candidate(1, Instant.parse("2026-10-01T00:00:00Z")), candidate(2, null)));

        List<CuratedPrompt> suggestions = new PickCuratedPromptService(port, RandomGenerator.getDefault())
                .suggestions(USER, "Travel", "A2", 10);

        assertThat(suggestions).extracting(CuratedPrompt::questionId, CuratedPrompt::done)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(new UUID(0, 2), false),
                        org.assertj.core.groups.Tuple.tuple(new UUID(0, 1), true));
    }
}
