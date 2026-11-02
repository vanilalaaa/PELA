package com.pela.writing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.identity.domain.UserAccount;
import com.pela.rag.application.port.in.GenerateWritingPromptUseCase;
import com.pela.rag.application.port.in.GenerateWritingPromptUseCase.GenerateCommand;
import com.pela.rag.application.port.in.GenerationResult.Generated;
import com.pela.rag.application.port.in.GenerationResult.NeedsFallback;
import com.pela.rag.application.port.in.GenerationResult.Source;
import com.pela.rag.application.port.in.ServedPrompt;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.ValidationFailedException;
import com.pela.support.MutableClock;
import com.pela.writing.application.port.in.PickCuratedPromptUseCase;
import com.pela.writing.application.port.in.PickCuratedPromptUseCase.CuratedPrompt;
import com.pela.writing.application.port.in.WritingPromptUseCase.PromptChoice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Điều phối UC-24 ở writing: đề AI nếu rag trả về, nếu không thì đề có sẵn (BR-7). */
class WritingPromptServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    private final GenerateWritingPromptUseCase rag = mock(GenerateWritingPromptUseCase.class);
    private final PickCuratedPromptUseCase curated = mock(PickCuratedPromptUseCase.class);
    private final GetCurrentUserUseCase users = mock(GetCurrentUserUseCase.class);
    private final SettingsPort settings = mock(SettingsPort.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final WritingPromptService service = new WritingPromptService(rag, curated, users, settings, clock);

    @BeforeEach
    void setUp() {
        UserAccount account = mock(UserAccount.class);
        when(account.cefrLevel()).thenReturn("A2");
        when(users.currentUser(USER)).thenReturn(account);
        when(settings.requiredInt("ai.question_gen_p95_budget_ms")).thenReturn(4000);
    }

    @Test
    void servesTheAiPromptAndTheDeadlineIsNowPlusTheBudget() {
        ServedPrompt ai = new ServedPrompt(UUID.randomUUID(), "Write about your trip.", "email", 50, 100);
        when(rag.generate(any(), any())).thenReturn(new Generated(ai, Source.GENERATED));

        PromptChoice choice = service.generate(USER, "  Travel ", "email");

        assertThat(choice).isEqualTo(new PromptChoice("ai_generated", ai.cacheId(), null, "Write about your trip.",
                "email", 50, 100, false, null));
        ArgumentCaptor<GenerateCommand> command = ArgumentCaptor.forClass(GenerateCommand.class);
        verify(rag).generate(org.mockito.ArgumentMatchers.eq(USER), command.capture());
        assertThat(command.getValue()).isEqualTo(new GenerateCommand("Travel", "A2", "email", NOW.plusMillis(4000)));
        verifyNoInteractions(curated);
    }

    @Test
    void cacheHitIsReportedAsSuch() {
        ServedPrompt cached = new ServedPrompt(UUID.randomUUID(), "Cached.", "paragraph", 30, 60);
        when(rag.generate(any(), any())).thenReturn(new Generated(cached, Source.CACHE_HIT));

        assertThat(service.generate(USER, "Travel", null).cacheHit()).isTrue();
    }

    @Test
    void fallsBackToABankPromptAndReportsTheReason() {
        UUID id = UUID.randomUUID();
        when(rag.generate(any(), any())).thenReturn(new NeedsFallback("BELOW_THRESHOLD"));
        when(curated.pick(USER, "Travel", "A2")).thenReturn(Optional.of(new CuratedPrompt(id, "Bank prompt.", "email",
                20, 40, false)));

        PromptChoice choice = service.generate(USER, "Travel", null);

        assertThat(choice).isEqualTo(new PromptChoice("bank", null, id, "Bank prompt.", "email", 20, 40, false,
                "BELOW_THRESHOLD"));
    }

    @Test
    void noPromptAtAllIsNotFound() {
        when(rag.generate(any(), any())).thenReturn(new NeedsFallback("BELOW_THRESHOLD"));
        when(curated.pick(USER, "Travel", "A2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(USER, "Travel", null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void learnerWithoutALevelCannotRequestAPrompt() {
        UserAccount noLevel = mock(UserAccount.class);
        when(users.currentUser(USER)).thenReturn(noLevel);

        assertThatThrownBy(() -> service.generate(USER, "Travel", null)).isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("level");
        verifyNoInteractions(rag, curated);
    }

    @Test
    void blankOrOverlongTopicIsRejected() {
        assertThatThrownBy(() -> service.generate(USER, " ", null)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.generate(USER, null, null)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> service.generate(USER, "x".repeat(101), null))
                .isInstanceOf(ValidationFailedException.class);
        verifyNoInteractions(rag);
    }

    @Test
    void suggestionsUseTheLearnersLevelAndClampTheLimit() {
        when(curated.suggestions(USER, "Travel", "A2", 20)).thenReturn(List.of());
        when(curated.suggestions(USER, "Travel", "A2", 1)).thenReturn(List.of());

        service.suggestions(USER, "Travel", 500);
        service.suggestions(USER, "Travel", 0);

        verify(curated).suggestions(USER, "Travel", "A2", 20);
        verify(curated).suggestions(USER, "Travel", "A2", 1);
    }
}
