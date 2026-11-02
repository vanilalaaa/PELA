package com.pela.writing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.identity.domain.UserAccount;
import com.pela.rag.application.port.in.GetServedPromptUseCase;
import com.pela.rag.application.port.in.ServedPrompt;
import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.TooManyRequestsException;
import com.pela.shared.domain.exception.ValidationFailedException;
import com.pela.srs.application.port.in.ReviewQueueUseCase;
import com.pela.writing.application.port.in.SubmitWritingUseCase.SubmitCommand;
import com.pela.writing.application.port.in.SubmitWritingUseCase.SubmitResult;
import com.pela.writing.application.port.out.GradingTriggerPort;
import com.pela.writing.application.port.out.WritingPromptPort;
import com.pela.writing.application.port.out.WritingPromptPort.BankPrompt;
import com.pela.writing.application.port.out.WritingSubmissionPort;
import com.pela.writing.application.port.out.WritingSubmissionPort.Existing;
import com.pela.writing.application.port.out.WritingSubmissionPort.NewSubmission;
import com.pela.writing.application.port.out.WritingSubmissionPort.QuotaUsage;
import com.pela.writing.application.port.out.WritingSubmissionPort.SuggestionRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WritingSubmissionServiceTest {

    /** 10:00 giờ Việt Nam ngày 02/11/2026. */
    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-11-01T17:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final String TEXT_25_WORDS = "word ".repeat(25).strip();

    private final WritingSubmissionPort submissions = mock(WritingSubmissionPort.class);
    private final WritingPromptPort prompts = mock(WritingPromptPort.class);
    private final GradingTriggerPort trigger = mock(GradingTriggerPort.class);
    private final GetCurrentUserUseCase users = mock(GetCurrentUserUseCase.class);
    private final VocabularyCatalogUseCase vocabulary = mock(VocabularyCatalogUseCase.class);
    private final ReviewQueueUseCase reviewQueue = mock(ReviewQueueUseCase.class);
    private final GetServedPromptUseCase servedPrompts = mock(GetServedPromptUseCase.class);
    private final SettingsPort settings = key -> key.equals("writing.daily_limit") ? Optional.of("5") : Optional.empty();

    private WritingSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new WritingSubmissionService(submissions, prompts, trigger, users, vocabulary, reviewQueue, settings,
                () -> NOW, servedPrompts);
        UserAccount learner = mock(UserAccount.class);
        when(learner.timezone()).thenReturn("Asia/Ho_Chi_Minh");
        when(users.currentUser(USER)).thenReturn(learner);
        when(submissions.find(any())).thenReturn(Optional.empty());
        when(submissions.insert(any())).thenReturn(true);
        when(submissions.quotaUsage(any(), any(), any())).thenReturn(new QuotaUsage(0, 0));
    }

    private static SubmitCommand freeform(UUID id, String text) {
        return new SubmitCommand(id, "freeform", null, null, text);
    }

    @Test
    void acceptsSubmissionAsPendingAndTriggersGrading() {
        UUID id = UUID.randomUUID();

        SubmitResult result = service.submit(USER, freeform(id, "  " + TEXT_25_WORDS + "  "));

        assertThat(result).isEqualTo(new SubmitResult(id, "pending"));
        ArgumentCaptor<NewSubmission> captor = ArgumentCaptor.forClass(NewSubmission.class);
        verify(submissions).insert(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("pending");
        assertThat(captor.getValue().wordCount()).isEqualTo(25);
        assertThat(captor.getValue().text()).isEqualTo(TEXT_25_WORDS);
        assertThat(captor.getValue().submittedAt()).isEqualTo(NOW);
        verify(submissions).quotaUsage(USER, DAY_START, DAY_START.plusSeconds(86400));
        verify(trigger).gradeSoon(id);
    }

    @Test
    void resendingSameIdIsIdempotent() {
        UUID id = UUID.randomUUID();
        when(submissions.find(id)).thenReturn(Optional.of(new Existing(id, USER, "graded")));

        assertThat(service.submit(USER, freeform(id, TEXT_25_WORDS))).isEqualTo(new SubmitResult(id, "graded"));
        verify(submissions, never()).insert(any());
        verify(trigger, never()).gradeSoon(any());
    }

    @Test
    void idOfAnotherUserConflicts() {
        UUID id = UUID.randomUUID();
        when(submissions.find(id)).thenReturn(Optional.of(new Existing(id, UUID.randomUUID(), "pending")));

        assertThatThrownBy(() -> service.submit(USER, freeform(id, TEXT_25_WORDS)))
                .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "SUBMISSION_ID_CONFLICT");
    }

    @Test
    void rejectsWordCountOutsideRange() {
        assertThatThrownBy(() -> service.submit(USER, freeform(UUID.randomUUID(), "word ".repeat(19))))
                .isInstanceOf(ValidationFailedException.class)
                .hasFieldOrPropertyWithValue("code", "WORD_COUNT_OUT_OF_RANGE");
        assertThatThrownBy(() -> service.submit(USER, freeform(UUID.randomUUID(), "word ".repeat(301))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void bankPromptNarrowsWordRange() {
        UUID q = UUID.randomUUID();
        when(prompts.activePrompt(q)).thenReturn(Optional.of(new BankPrompt(q, "Describe your house.", 30, 120, null)));

        assertThatThrownBy(() -> service.submit(USER, new SubmitCommand(UUID.randomUUID(), "bank", q, null, TEXT_25_WORDS)))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(service.submit(USER, new SubmitCommand(UUID.randomUUID(), "bank", q, null, "word ".repeat(40))).status())
                .isEqualTo("pending");
    }

    @Test
    void unknownBankPromptIsRejected() {
        when(prompts.activePrompt(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(USER,
                new SubmitCommand(UUID.randomUUID(), "bank", UUID.randomUUID(), null, TEXT_25_WORDS)))
                .isInstanceOf(NotFoundException.class).hasFieldOrPropertyWithValue("code", "PROMPT_NOT_FOUND");
    }

    @Test
    void aiPromptServedToTheLearnerIsStoredWithItsCacheIdAndNarrowsTheWordRange() {
        UUID cacheId = UUID.randomUUID();
        when(servedPrompts.servedTo(USER, cacheId)).thenReturn(Optional.of(
                new ServedPrompt(cacheId, "Write an email to a hotel.", "email", 30, 120)));

        assertThatThrownBy(() -> service.submit(USER,
                new SubmitCommand(UUID.randomUUID(), "ai_generated", null, cacheId, TEXT_25_WORDS)))
                .hasFieldOrPropertyWithValue("code", "WORD_COUNT_OUT_OF_RANGE");
        UUID id = UUID.randomUUID();
        assertThat(service.submit(USER, new SubmitCommand(id, "ai_generated", null, cacheId, "word ".repeat(40)))
                .status()).isEqualTo("pending");

        ArgumentCaptor<NewSubmission> stored = ArgumentCaptor.forClass(NewSubmission.class);
        verify(submissions).insert(stored.capture());
        assertThat(stored.getValue().promptSource()).isEqualTo("ai_generated");
        assertThat(stored.getValue().ragCacheId()).isEqualTo(cacheId);
        assertThat(stored.getValue().promptQuestionId()).isNull();
        assertThat(stored.getValue().promptText()).isEqualTo("Write an email to a hotel."); // snapshot đề
    }

    @Test
    void aiPromptThatWasNotServedToTheLearnerIsRejected() {
        when(servedPrompts.servedTo(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(USER,
                new SubmitCommand(UUID.randomUUID(), "ai_generated", null, UUID.randomUUID(), TEXT_25_WORDS)))
                .isInstanceOf(NotFoundException.class).hasFieldOrPropertyWithValue("code", "PROMPT_NOT_FOUND");
    }

    @Test
    void aiPromptWithoutACacheIdIsAValidationError() {
        assertThatThrownBy(() -> service.submit(USER,
                new SubmitCommand(UUID.randomUUID(), "ai_generated", null, null, TEXT_25_WORDS)))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void quotaExhaustedKeepsDraftAndReturns429() {
        UUID id = UUID.randomUUID();
        when(submissions.quotaUsage(any(), any(), any())).thenReturn(new QuotaUsage(4, 1));

        assertThatThrownBy(() -> service.submit(USER, freeform(id, TEXT_25_WORDS)))
                .isInstanceOf(TooManyRequestsException.class)
                .hasFieldOrPropertyWithValue("code", "WRITING_QUOTA_EXCEEDED");

        ArgumentCaptor<NewSubmission> captor = ArgumentCaptor.forClass(NewSubmission.class);
        verify(submissions).insert(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("draft");
        assertThat(captor.getValue().submittedAt()).isNull();
        verify(trigger, never()).gradeSoon(any());
    }

    @Test
    void quotaMessageMentionsSubmissionsAwaitingGrading() {
        when(submissions.quotaUsage(any(), any(), any())).thenReturn(new QuotaUsage(3, 2));

        assertThatThrownBy(() -> service.submit(USER, freeform(UUID.randomUUID(), TEXT_25_WORDS)))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("2 submission(s) still awaiting grading")
                .satisfies(e -> assertThat(((TooManyRequestsException) e).details())
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("gradedToday", 3).containsEntry("awaitingGrading", 2));
    }

    @Test
    void quotaMessageWithoutPendingDoesNotMentionThem() {
        when(submissions.quotaUsage(any(), any(), any())).thenReturn(new QuotaUsage(5, 0));

        assertThatThrownBy(() -> service.submit(USER, freeform(UUID.randomUUID(), TEXT_25_WORDS)))
                .hasMessage("Daily grading quota is used up");
    }

    @Test
    void underLimitWithPendingSubmissionsIsAccepted() {
        when(submissions.quotaUsage(any(), any(), any())).thenReturn(new QuotaUsage(2, 2));

        assertThat(service.submit(USER, freeform(UUID.randomUUID(), TEXT_25_WORDS)).status()).isEqualTo("pending");
    }

    @Test
    void quotaDayFollowsLearnerTimezoneAcrossMidnight() {
        // 23:59:59 ngày 01/11 giờ Việt Nam (UTC+7) → cửa sổ 01/11; 1 giây sau → cửa sổ 02/11
        Instant beforeMidnight = Instant.parse("2026-11-01T16:59:59Z");
        new WritingSubmissionService(submissions, prompts, trigger, users, vocabulary, reviewQueue, settings,
                () -> beforeMidnight, servedPrompts).submit(USER, freeform(UUID.randomUUID(), TEXT_25_WORDS));
        verify(submissions).quotaUsage(USER, Instant.parse("2026-10-31T17:00:00Z"), Instant.parse("2026-11-01T17:00:00Z"));

        new WritingSubmissionService(submissions, prompts, trigger, users, vocabulary, reviewQueue, settings,
                () -> beforeMidnight.plusSeconds(1), servedPrompts).submit(USER, freeform(UUID.randomUUID(), TEXT_25_WORDS));
        verify(submissions).quotaUsage(USER, Instant.parse("2026-11-01T17:00:00Z"), Instant.parse("2026-11-02T17:00:00Z"));
    }

    @Test
    void quotaDayUsesEachLearnersOwnTimezone() {
        UserAccount london = mock(UserAccount.class);
        when(london.timezone()).thenReturn("Europe/London");
        UUID other = UUID.randomUUID();
        when(users.currentUser(other)).thenReturn(london);

        // 03:00Z ngày 02/11: London là 03:00 ngày 02/11 (GMT)
        service.submit(other, freeform(UUID.randomUUID(), TEXT_25_WORDS));

        verify(submissions).quotaUsage(other, Instant.parse("2026-11-02T00:00:00Z"), Instant.parse("2026-11-03T00:00:00Z"));
    }

    @Test
    void draftIsSubmittedLaterWithSameId() {
        UUID id = UUID.randomUUID();
        when(submissions.find(id)).thenReturn(Optional.of(new Existing(id, USER, "draft")));
        when(submissions.updateDraft(any())).thenReturn(true);

        assertThat(service.submit(USER, freeform(id, TEXT_25_WORDS)).status()).isEqualTo("pending");
        verify(submissions).updateDraft(any());
        verify(trigger).gradeSoon(id);
    }

    @Test
    void addSuggestedWordCreatesPrivateWordAndPushesToReview() {
        UUID suggestionId = UUID.randomUUID();
        UUID vocabId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(submissions.suggestion(suggestionId)).thenReturn(Optional.of(new SuggestionRecord(suggestionId,
                UUID.randomUUID(), USER, "receive", "nhận", null, "suggested", null, null)));
        when(vocabulary.addPrivateWord(USER, "receive", "nhận", null, null)).thenReturn(vocabId);
        when(reviewQueue.pushForReviewToday(USER, vocabId, LocalDate.of(2026, 11, 2))).thenReturn(cardId);

        var added = service.add(USER, suggestionId);

        assertThat(added.status()).isEqualTo("added");
        assertThat(added.reviewCardId()).isEqualTo(cardId);
        verify(submissions).markSuggestionAdded(suggestionId, "added", vocabId, cardId);
    }

    @Test
    void addSuggestedWordIsIdempotentAndOwnerOnly() {
        UUID suggestionId = UUID.randomUUID();
        when(submissions.suggestion(suggestionId)).thenReturn(Optional.of(new SuggestionRecord(suggestionId,
                UUID.randomUUID(), USER, "advice", "lời khuyên", "A2", "auto_added", UUID.randomUUID(), null)));

        assertThat(service.add(USER, suggestionId).status()).isEqualTo("auto_added");
        assertThatThrownBy(() -> service.add(UUID.randomUUID(), suggestionId)).isInstanceOf(NotFoundException.class);
        verify(reviewQueue, never()).pushForReviewToday(any(), any(), any());
        verify(submissions, never()).markSuggestionAdded(any(), any(), any(), any());
    }

    @Test
    void historyLimitIsClamped() {
        service.history(USER, 1000);
        verify(submissions).history(eq(USER), eq(50));
    }
}
