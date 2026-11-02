package com.pela.rag.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pela.rag.application.port.in.GenerateWritingPromptUseCase.GenerateCommand;
import com.pela.rag.application.port.in.GenerationResult;
import com.pela.rag.application.port.in.GenerationResult.Generated;
import com.pela.rag.application.port.in.GenerationResult.NeedsFallback;
import com.pela.rag.application.port.in.GenerationResult.Source;
import com.pela.rag.application.port.in.ServedPrompt;
import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.rag.application.port.out.GenerationLogPort;
import com.pela.rag.application.port.out.LearnerContextPort;
import com.pela.rag.application.port.out.LearnerContextPort.LearnerContext;
import com.pela.rag.application.port.out.PromptCachePort;
import com.pela.rag.application.port.out.PromptCachePort.CacheLookup;
import com.pela.rag.application.port.out.PromptCachePort.NewCacheEntry;
import com.pela.rag.application.port.out.PromptExampleDetailsPort;
import com.pela.rag.application.port.out.PromptExampleDetailsPort.ExampleDetails;
import com.pela.rag.application.port.out.WritingPromptVectorPort;
import com.pela.rag.domain.FallbackReason;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.ContentFilterPort;
import com.pela.shared.application.port.out.ContentFilterPort.ContentCheck;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.LlmPort.LlmResult;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.ValidationFailedException;
import com.pela.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** UC-24: mọi nhánh của chuỗi sinh đề; sự cố AI nào cũng trả NeedsFallback chứ không ném lỗi (PER-4). */
class GenerateWritingPromptServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z"); // 17:00 giờ Việt Nam
    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID TOP1 = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID TOP2 = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final float[] QUERY_VECTOR = {1f, 0f};
    private static final float[] PROMPT_VECTOR = {0f, 1f};

    private final LearnerContextPort learners = mock(LearnerContextPort.class);
    private final PromptCachePort cache = mock(PromptCachePort.class);
    private final WritingPromptVectorPort vectors = mock(WritingPromptVectorPort.class);
    private final PromptExampleDetailsPort details = mock(PromptExampleDetailsPort.class);
    private final GenerationLogPort log = mock(GenerationLogPort.class);
    private final EmbeddingPort embedding = mock(EmbeddingPort.class);
    private final LlmPort llm = mock(LlmPort.class);
    private final ContentFilterPort filter = mock(ContentFilterPort.class);
    private final SettingsPort settings = mock(SettingsPort.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final UUID cacheId = UUID.fromString("00000000-0000-4000-8000-00000000c001");

    private GenerateWritingPromptService service;

    @BeforeEach
    void setUp() {
        service = new GenerateWritingPromptService(learners, cache, vectors, details, log, embedding, llm, filter,
                settings, clock, () -> cacheId);
        when(learners.context(USER)).thenReturn(new LearnerContext(List.of("food", "music"),
                ZoneId.of("Asia/Ho_Chi_Minh")));
        when(settings.requiredInt("writing.ai_prompt_daily_limit")).thenReturn(10);
        when(settings.requiredInt("rag.retrieval.top_k")).thenReturn(5);
        when(settings.requiredDouble("rag.retrieval.min_similarity")).thenReturn(0.65);
        when(settings.requiredDouble("rag.cache.min_similarity")).thenReturn(0.92);
        when(settings.requiredInt("rag.cache.dedupe_days")).thenReturn(14);
        when(settings.requiredInt("rag.cache.ttl_days")).thenReturn(30);
        when(settings.requiredDouble("rag.generation.max_bank_similarity")).thenReturn(0.95);
        when(cache.countServedBetween(any(), any(), any())).thenReturn(0);
        when(cache.findHit(any())).thenReturn(Optional.empty());
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_QUERY))).thenReturn(QUERY_VECTOR);
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_DOCUMENT))).thenReturn(PROMPT_VECTOR);
        when(vectors.nearest(eq(QUERY_VECTOR), eq("Travel"), eq("A2"), anyInt())).thenReturn(List.of(
                similar(TOP1, "Write an email to a friend about your trip.", 0.80),
                similar(TOP2, "Describe a holiday you enjoyed.", 0.74)));
        when(vectors.nearest(eq(PROMPT_VECTOR), eq("Travel"), eq("A2"), anyInt())).thenReturn(List.of(
                similar(TOP1, "Write an email to a friend about your trip.", 0.40)));
        when(details.details(any())).thenReturn(Map.of(
                TOP1, new ExampleDetails("email", 50, 100),
                TOP2, new ExampleDetails("paragraph", 50, 100)));
        when(filter.check(anyString())).thenReturn(new ContentCheck(true, List.of()));
        when(llm.generateJson(eq("rag_question_generation"), any(), eq(GeneratedPromptResponse.class), any()))
                .thenReturn(new LlmResult<>(new GeneratedPromptResponse("Write an email to a hotel about a booking.",
                        "email", 50, 100), 2, "gemini-test"));
    }

    private static SimilarWritingPrompt similar(UUID id, String text, double similarity) {
        return new SimilarWritingPrompt(id, text, "Travel", "A2", similarity);
    }

    private GenerateCommand command(String taskType) {
        return new GenerateCommand("Travel", "A2", taskType, NOW.plusMillis(4000));
    }

    private GenerationResult run() {
        return service.generate(USER, command(null));
    }

    private void assertFallback(GenerationResult result, FallbackReason reason) {
        assertThat(result).isEqualTo(NeedsFallback.of(reason));
        verify(cache, never()).storeAndServe(any(), any(), any());
        ArgumentCaptor<GenerationLogPort.Entry> logged = ArgumentCaptor.forClass(GenerationLogPort.Entry.class);
        verify(log).record(logged.capture());
        assertThat(logged.getValue().outcome()).isEqualTo("fallback");
        assertThat(logged.getValue().reason()).isEqualTo(reason);
    }

    @Test
    void generatesStoresInCacheAndLogsGenerated() {
        GenerationResult result = run();

        ServedPrompt expected = new ServedPrompt(cacheId, "Write an email to a hotel about a booking.", "email", 50, 100);
        assertThat(result).isEqualTo(new Generated(expected, Source.GENERATED));
        ArgumentCaptor<NewCacheEntry> stored = ArgumentCaptor.forClass(NewCacheEntry.class);
        verify(cache).storeAndServe(stored.capture(), eq(USER), eq(NOW));
        assertThat(stored.getValue().cacheId()).isEqualTo(cacheId);
        assertThat(stored.getValue().queryEmbedding()).isEqualTo(QUERY_VECTOR);
        assertThat(stored.getValue().taskType()).isEqualTo("email");
        assertThat(stored.getValue().sourceTemplateId()).isEqualTo(TOP1); // BR-25: Top-1
        assertThat(stored.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        ArgumentCaptor<GenerationLogPort.Entry> logged = ArgumentCaptor.forClass(GenerationLogPort.Entry.class);
        verify(log).record(logged.capture());
        assertThat(logged.getValue().outcome()).isEqualTo("generated");
        assertThat(logged.getValue().reason()).isNull();
        assertThat(logged.getValue().top1Similarity()).isEqualTo(0.80);
    }

    @Test
    void sendsTopicLevelInterestsAndNumberedExamplesToTheLlm() {
        run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(llm).generateJson(eq("rag_question_generation"), vars.capture(), eq(GeneratedPromptResponse.class),
                any());
        assertThat(vars.getValue()).containsEntry("topic", "Travel").containsEntry("level", "A2")
                .containsEntry("interests", "food, music")
                .containsEntry("task_type_rule", "keep the task type of the first example");
        assertThat(vars.getValue().get("examples")).isEqualTo("""
                1. [email, 50-100 words] Write an email to a friend about your trip.
                2. [paragraph, 50-100 words] Describe a holiday you enjoyed.""");
    }

    @Test
    void llmTimeoutIsTheRemainingBudget() {
        clock.advance(Duration.ofMillis(1500));

        service.generate(USER, command(null));

        ArgumentCaptor<Duration> timeout = ArgumentCaptor.forClass(Duration.class);
        verify(llm).generateJson(anyString(), any(), eq(GeneratedPromptResponse.class), timeout.capture());
        assertThat(timeout.getValue()).isEqualTo(Duration.ofMillis(2500));
    }

    @Test
    void requestedTaskTypeNarrowsTheCacheLookupAndTheRule() {
        GenerationResult result = service.generate(USER, command("essay"));

        // essay không có trong đề mẫu nhưng learner đã chọn: LLM bị yêu cầu đúng dạng đó (đề trả về là email → sai dạng)
        assertThat(result).isEqualTo(NeedsFallback.of(FallbackReason.INVALID_OUTPUT));
        ArgumentCaptor<CacheLookup> lookup = ArgumentCaptor.forClass(CacheLookup.class);
        verify(cache).findHit(lookup.capture());
        assertThat(lookup.getValue().taskType()).contains("essay");
        assertThat(lookup.getValue().minSimilarity()).isEqualTo(0.92);
        assertThat(lookup.getValue().notServedSince()).isEqualTo(NOW.minus(Duration.ofDays(14)));
    }

    @Test
    void unknownTaskTypeIsAValidationError() {
        assertThatThrownBy(() -> service.generate(USER, command("poem"))).isInstanceOf(ValidationFailedException.class);
        verifyNoInteractions(embedding, llm);
    }

    @Test
    void cacheHitSkipsRetrievalAndTheLlm() {
        ServedPrompt cached = new ServedPrompt(UUID.randomUUID(), "Cached prompt.", "email", 50, 100);
        when(cache.findHit(any())).thenReturn(Optional.of(cached));

        GenerationResult result = run();

        assertThat(result).isEqualTo(new Generated(cached, Source.CACHE_HIT));
        verify(cache).serveHit(cached.cacheId(), USER, NOW);
        verifyNoInteractions(llm);
        verify(vectors, never()).nearest(any(), any(), any(), anyInt());
        ArgumentCaptor<GenerationLogPort.Entry> logged = ArgumentCaptor.forClass(GenerationLogPort.Entry.class);
        verify(log).record(logged.capture());
        assertThat(logged.getValue().outcome()).isEqualTo("cache_hit");
    }

    @Test
    void dailyLimitFallsBackBeforeAnyAiCallAndCountsTheLearnersLocalDay() {
        when(cache.countServedBetween(any(), any(), any())).thenReturn(10);

        assertFallback(run(), FallbackReason.DAILY_LIMIT);

        verifyNoInteractions(embedding, llm);
        // 17:00 UTC+7 ngày 03/11: ngày địa phương là [02/11 17:00 UTC, 03/11 17:00 UTC)
        verify(cache).countServedBetween(USER, Instant.parse("2026-11-02T17:00:00Z"),
                Instant.parse("2026-11-03T17:00:00Z"));
    }

    @Test
    void belowRetrievalThresholdFallsBackWithoutCallingTheLlm() {
        when(vectors.nearest(eq(QUERY_VECTOR), eq("Travel"), eq("A2"), anyInt())).thenReturn(List.of(
                similar(TOP1, "Write an email to a friend about your trip.", 0.60)));

        assertFallback(run(), FallbackReason.BELOW_THRESHOLD);

        verifyNoInteractions(llm);
        ArgumentCaptor<GenerationLogPort.Entry> logged = ArgumentCaptor.forClass(GenerationLogPort.Entry.class);
        verify(log).record(logged.capture());
        assertThat(logged.getValue().top1Similarity()).isEqualTo(0.60);
    }

    @Test
    void emptyBankFallsBackAsBelowThreshold() {
        when(vectors.nearest(eq(QUERY_VECTOR), eq("Travel"), eq("A2"), anyInt())).thenReturn(List.of());

        assertFallback(run(), FallbackReason.BELOW_THRESHOLD);

        verifyNoInteractions(llm);
    }

    @Test
    void embeddingFailureFallsBack() {
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_QUERY)))
                .thenThrow(new AiCallException(Kind.UPSTREAM_ERROR, "down"));

        assertFallback(run(), FallbackReason.NO_EMBEDDING);
    }

    @Test
    void embeddingTimeoutFallsBackAsTimeout() {
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_QUERY)))
                .thenThrow(new AiCallException(Kind.TIMEOUT, "slow"));

        assertFallback(run(), FallbackReason.TIMEOUT);
    }

    @Test
    void everyLlmErrorKindFallsBackWithItsReason() {
        Map<Kind, FallbackReason> expected = Map.of(
                Kind.TIMEOUT, FallbackReason.TIMEOUT,
                Kind.INVALID_JSON, FallbackReason.INVALID_JSON,
                Kind.SAFETY_BLOCKED, FallbackReason.SAFETY_BLOCKED,
                Kind.RATE_LIMITED, FallbackReason.UPSTREAM_ERROR,
                Kind.UPSTREAM_ERROR, FallbackReason.UPSTREAM_ERROR);
        for (Map.Entry<Kind, FallbackReason> e : expected.entrySet()) {
            org.mockito.Mockito.clearInvocations(cache, log);
            when(llm.generateJson(anyString(), any(), eq(GeneratedPromptResponse.class), any()))
                    .thenThrow(new AiCallException(e.getKey(), "boom"));

            assertFallback(run(), e.getValue());
        }
    }

    @Test
    void expiredBudgetFallsBackAsTimeoutWithoutCallingTheLlm() {
        clock.advance(Duration.ofMillis(4000));

        assertFallback(run(), FallbackReason.TIMEOUT);

        verifyNoInteractions(llm);
    }

    @Test
    void budgetThatRunsOutAfterRetrievalNeverReachesTheLlm() {
        when(embedding.embed(anyString(), eq(TaskType.RETRIEVAL_QUERY))).thenAnswer(inv -> {
            clock.advance(Duration.ofMillis(4500));
            return QUERY_VECTOR;
        });

        assertFallback(run(), FallbackReason.TIMEOUT);

        verifyNoInteractions(llm);
    }

    @Test
    void outputWithTheWrongTaskTypeOrWordRangeFallsBack() {
        when(llm.generateJson(anyString(), any(), eq(GeneratedPromptResponse.class), any()))
                .thenReturn(new LlmResult<>(new GeneratedPromptResponse("Write a poem.", "poem", 50, 100), 2, "m"));
        assertFallback(run(), FallbackReason.INVALID_OUTPUT);

        org.mockito.Mockito.clearInvocations(cache, log);
        when(llm.generateJson(anyString(), any(), eq(GeneratedPromptResponse.class), any()))
                .thenReturn(new LlmResult<>(new GeneratedPromptResponse("Write an email.", "email", 20, 300), 2, "m"));
        assertFallback(run(), FallbackReason.INVALID_OUTPUT);
    }

    @Test
    void contentFilterRejectionFallsBackAndNothingIsCached() {
        when(filter.check(anyString())).thenReturn(new ContentCheck(false, List.of("badword")));

        assertFallback(run(), FallbackReason.FILTERED);
    }

    @Test
    void promptTooSimilarToABankPromptFallsBack() {
        when(vectors.nearest(eq(PROMPT_VECTOR), eq("Travel"), eq("A2"), anyInt())).thenReturn(List.of(
                similar(TOP1, "Write an email to a friend about your trip.", 0.96)));

        assertFallback(run(), FallbackReason.INVALID_OUTPUT);
    }

    @Test
    void aFailingLogDoesNotBreakServingAPrompt() {
        org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(log).record(any());

        assertThat(run()).isInstanceOf(Generated.class);
    }
}
