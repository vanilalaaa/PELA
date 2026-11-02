package com.pela.writing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.identity.domain.UserAccount;
import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase;
import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase.CatalogEntry;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.ContentFilterPort;
import com.pela.shared.application.port.out.ContentFilterPort.ContentCheck;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.LlmPort.LlmResult;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.srs.application.port.in.ReviewQueueUseCase;
import com.pela.writing.application.port.out.WritingGradedEventPort;
import com.pela.writing.application.port.out.WritingGradedEventPort.WritingGraded;
import com.pela.writing.application.port.out.WritingPromptPort;
import com.pela.writing.application.port.out.WritingSubmissionPort;
import com.pela.writing.application.port.out.WritingSubmissionPort.Claimed;
import com.pela.writing.application.port.out.WritingSubmissionPort.GradedSubmission;
import com.pela.writing.application.usecase.GradingResponse.Criteria;
import com.pela.writing.application.usecase.GradingResponse.Issue;
import com.pela.writing.application.usecase.GradingResponse.VocabCandidate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WritingGradingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private static final UUID SUBMISSION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final String TEXT = "I goes to school every day. My school is big. I like my school very much.";
    private static final Map<String, String> SETTINGS = Map.of(
            "ai.grading_timeout_ms", "8000",
            "writing.grading_max_attempts", "3",
            "writing.grading_retry_delay_minutes", "5",
            "writing.repetition_min_count", "3",
            "writing.vocab_suggestions_max", "3",
            "star.writing_bands",
            "[{\"min_score\":90,\"stars\":5},{\"min_score\":75,\"stars\":4},{\"min_score\":60,\"stars\":3},"
                    + "{\"min_score\":40,\"stars\":2},{\"min_score\":20,\"stars\":1}]");

    private final WritingSubmissionPort submissions = mock(WritingSubmissionPort.class);
    private final WritingPromptPort prompts = mock(WritingPromptPort.class);
    private final LlmPort llm = mock(LlmPort.class);
    private final GetCurrentUserUseCase users = mock(GetCurrentUserUseCase.class);
    private final VocabularyCatalogUseCase vocabulary = mock(VocabularyCatalogUseCase.class);
    private final ReviewQueueUseCase reviewQueue = mock(ReviewQueueUseCase.class);
    private final WritingGradedEventPort events = mock(WritingGradedEventPort.class);
    private final SettingsPort settings = key -> Optional.ofNullable(SETTINGS.get(key));
    /** Chặn mọi đoạn có chữ "badword". */
    private final ContentFilterPort filter = text -> text.contains("badword")
            ? new ContentCheck(false, List.of("badword")) : new ContentCheck(true, List.of());

    private WritingGradingService service;

    @BeforeEach
    void setUp() {
        service = new WritingGradingService(submissions, prompts, llm, filter, users, vocabulary, reviewQueue, settings,
                () -> NOW, UUID::randomUUID, events);
        UserAccount learner = mock(UserAccount.class);
        when(learner.cefrLevel()).thenReturn("B1");
        when(learner.timezone()).thenReturn("Asia/Ho_Chi_Minh");
        when(users.currentUser(USER)).thenReturn(learner);
        when(vocabulary.findCurated(any())).thenReturn(Map.of());
        when(reviewQueue.wordsInReviewPool(eq(USER), any())).thenReturn(Set.of());
        when(submissions.saveGraded(any())).thenReturn(true);
    }

    private void claimed(int attempts) {
        when(submissions.claim(eq(SUBMISSION), eq(NOW), any()))
                .thenReturn(Optional.of(new Claimed(SUBMISSION, USER, TEXT, null, null, 17, attempts)));
    }

    private static GradingResponse response(List<Issue> issues, List<String> rewrites, List<VocabCandidate> words,
            String comment) {
        return new GradingResponse(new Criteria(20, 18, 15, 22), issues, rewrites, words, comment);
    }

    private void llmReturns(GradingResponse response) {
        when(llm.generateJson(eq("writing_grading"), anyMap(), eq(GradingResponse.class), any(Duration.class)))
                .thenReturn(new LlmResult<>(response, 2, "gemini-3.8-flash"));
    }

    private GradedSubmission saved() {
        ArgumentCaptor<GradedSubmission> captor = ArgumentCaptor.forClass(GradedSubmission.class);
        verify(submissions).saveGraded(captor.capture());
        return captor.getValue();
    }

    @Test
    void gradesWithServerSideScoreStarsAndLocatedIssues() {
        claimed(1);
        llmReturns(response(List.of(new Issue(0, "goes", "go", "grammar", "error", "Chủ ngữ I dùng go."),
                        new Issue(1, "not in text", "x", "grammar", "error", "bỏ")),
                List.of("I go to school every day."), List.of(), "Bài viết tốt."));

        service.gradeOne(SUBMISSION);

        GradedSubmission g = saved();
        assertThat(g.score()).isEqualTo(75);
        assertThat(g.stars()).isEqualTo(4);
        assertThat(g.promptVersion()).isEqualTo(2);
        assertThat(g.model()).isEqualTo("gemini-3.8-flash");
        assertThat(g.gradedAt()).isEqualTo(NOW);
        assertThat(g.issues()).hasSize(1);
        assertThat(g.issues().get(0).issue().start()).isEqualTo(2);
        assertThat(g.aiFeedback()).containsEntry("comment", "Bài viết tốt.")
                .containsEntry("rewrites", List.of("I go to school every day."));
        verify(events).writingGraded(new WritingGraded(SUBMISSION, USER, 4, NOW));
    }

    @Test
    void noEventWhenAnotherWorkerAlreadySavedTheResult() {
        claimed(1);
        llmReturns(response(List.of(), List.of(), List.of(), "Ok."));
        when(submissions.saveGraded(any())).thenReturn(false);

        service.gradeOne(SUBMISSION);

        verify(events, never()).writingGraded(any());
    }

    @Test
    void invalidOrFilteredWordsAreDroppedWithoutFailingTheGrading() {
        claimed(1);
        llmReturns(response(List.of(), List.of(),
                List.of(new VocabCandidate("recieve", "receive", "misspelled", " ", "B1"),
                        new VocabCandidate("wether", "weather", "misspelled", "thời tiết", "Z1"),
                        new VocabCandidate("freind", "friend", "misspelled", "badword nghĩa", "A1"),
                        new VocabCandidate("buisness", "business", "misspelled", "kinh doanh", "A2")),
                "Ok."));

        service.gradeOne(SUBMISSION);

        assertThat(saved().suggestions()).extracting(sv -> sv.word().word()).containsExactly("business");
        verify(submissions, never()).markRetryQueued(any(), any());
        verify(events).writingGraded(any());
    }

    @Test
    void failedGradingPublishesNoEvent() {
        claimed(3);
        when(llm.generateJson(any(), anyMap(), any(), any())).thenThrow(new AiCallException(Kind.TIMEOUT, "t"));

        service.gradeOne(SUBMISSION);

        verify(events, never()).writingGraded(any());
    }

    @Test
    void promptGetsNumberedSentencesAndRepeatedWords() {
        claimed(1);
        llmReturns(response(List.of(), List.of(), List.of(), "Ok."));

        service.gradeOne(SUBMISSION);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(llm).generateJson(eq("writing_grading"), vars.capture(), eq(GradingResponse.class), any());
        assertThat(vars.getValue().get("numbered_sentences"))
                .isEqualTo("[0] I goes to school every day.\n[1] My school is big.\n[2] I like my school very much.");
        assertThat(vars.getValue().get("repeated_words")).isEqualTo("school (3x)");
        assertThat(vars.getValue()).containsEntry("level", "B1").containsEntry("word_count", "17")
                .containsEntry("min_words", "20").containsEntry("max_words", "300");
    }

    @Test
    void br15FiltersCommentRewritesAndIssues() {
        claimed(1);
        llmReturns(response(List.of(new Issue(0, "goes", "go", "grammar", "error", "badword explanation"),
                        new Issue(2, "very much", "a lot", "style", "error", "Tự nhiên hơn.")),
                List.of("badword rewrite", "Clean rewrite."), List.of(), "badword comment"));

        service.gradeOne(SUBMISSION);

        GradedSubmission g = saved();
        assertThat(g.issues()).singleElement().satisfies(i -> assertThat(i.issue().original()).isEqualTo("very much"));
        assertThat(g.aiFeedback()).containsEntry("comment", null).containsEntry("comment_filtered", true)
                .containsEntry("rewrites", List.of("Clean rewrite."));
    }

    @Test
    void br9AutoAddsCatalogueWordsAndSuggestsOthers() {
        claimed(1);
        UUID adviceId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        when(vocabulary.findCurated(any())).thenReturn(Map.of("advice", new CatalogEntry(adviceId, "A2")));
        when(reviewQueue.pushForReviewToday(USER, adviceId, LocalDate.of(2026, 11, 2))).thenReturn(cardId);
        llmReturns(response(List.of(), List.of(),
                List.of(new VocabCandidate("advices", "advice", "wrong_form", "lời khuyên", "A2"),
                        new VocabCandidate("recieve", "receive", "misspelled", "nhận", "B1"),
                        new VocabCandidate("happy", "glad", "better_alternative", "vui", "A2")),
                "Ok."));

        service.gradeOne(SUBMISSION);

        GradedSubmission g = saved();
        assertThat(g.suggestions()).hasSize(2);
        assertThat(g.suggestions().get(0).status()).isEqualTo("auto_added");
        assertThat(g.suggestions().get(0).vocabId()).isEqualTo(adviceId);
        assertThat(g.suggestions().get(0).reviewCardId()).isEqualTo(cardId);
        assertThat(g.suggestions().get(1).status()).isEqualTo("suggested");
        assertThat(g.suggestions().get(1).vocabId()).isNull();
        verify(reviewQueue, times(1)).pushForReviewToday(any(), any(), any());
    }

    @Test
    void retriesOnceThenSucceeds() {
        claimed(1);
        when(llm.generateJson(eq("writing_grading"), anyMap(), eq(GradingResponse.class), any(Duration.class)))
                .thenThrow(new AiCallException(Kind.TIMEOUT, "timeout"))
                .thenReturn(new LlmResult<>(response(List.of(), List.of(), List.of(), "Ok."), 2, "m"));

        service.gradeOne(SUBMISSION);

        verify(llm, times(2)).generateJson(any(), anyMap(), any(), any());
        saved();
    }

    @Test
    void twoFailuresQueueRetryWithDelay() {
        claimed(1);
        when(llm.generateJson(any(), anyMap(), any(), any())).thenThrow(new AiCallException(Kind.INVALID_JSON, "bad"));

        service.gradeOne(SUBMISSION);

        verify(llm, times(2)).generateJson(any(), anyMap(), any(), any());
        verify(submissions).markRetryQueued(SUBMISSION, NOW.plus(Duration.ofMinutes(5)));
        verify(submissions, never()).saveGraded(any());
        verify(submissions, never()).markFailed(any());
    }

    @Test
    void lastAttemptFailureMarksFailed() {
        claimed(3);
        when(llm.generateJson(any(), anyMap(), any(), any())).thenThrow(new AiCallException(Kind.UPSTREAM_ERROR, "503"));

        service.gradeOne(SUBMISSION);

        verify(submissions).markFailed(SUBMISSION);
        verify(submissions, never()).markRetryQueued(any(), any());
    }

    @Test
    void safetyBlockFailsWithoutRetry() {
        claimed(1);
        when(llm.generateJson(any(), anyMap(), any(), any())).thenThrow(new AiCallException(Kind.SAFETY_BLOCKED, "x"));

        service.gradeOne(SUBMISSION);

        verify(llm, times(1)).generateJson(any(), anyMap(), any(), any());
        verify(submissions).markFailed(SUBMISSION);
    }

    @Test
    void unexpectedErrorCountsAsFailedAttempt() {
        claimed(1);
        when(llm.generateJson(any(), anyMap(), any(), any())).thenThrow(new IllegalStateException("no active prompt"));

        service.gradeOne(SUBMISSION);

        verify(submissions).markRetryQueued(eq(SUBMISSION), any());
    }

    @Test
    void skipsWhenAnotherWorkerHoldsTheSubmission() {
        when(submissions.claim(any(), any(), any())).thenReturn(Optional.empty());

        service.gradeOne(SUBMISSION);

        verify(llm, never()).generateJson(any(), anyMap(), any(), any());
    }

    @Test
    void claimLeaseCoversTwoCalls() {
        claimed(1);
        llmReturns(response(List.of(), List.of(), List.of(), "Ok."));

        service.gradeOne(SUBMISSION);

        verify(submissions).claim(SUBMISSION, NOW, NOW.plusMillis(16000).plusSeconds(60));
    }
}
