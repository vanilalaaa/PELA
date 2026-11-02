package com.pela.rag.application.usecase;

import com.pela.rag.application.port.in.GenerateWritingPromptUseCase;
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
import com.pela.rag.domain.GeneratedPrompt;
import com.pela.rag.domain.LatencyBudget;
import com.pela.rag.domain.PromptExample;
import com.pela.rag.domain.PromptExamples;
import com.pela.rag.domain.RagDecisionPolicy;
import com.pela.rag.domain.RagQueryBuilder;
import com.pela.rag.domain.WritingTaskType;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.ContentFilterPort;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.IdGeneratorPort;
import com.pela.shared.application.port.out.LlmPort;
import com.pela.shared.application.port.out.LlmPort.LlmResult;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.ValidationFailedException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * UC-24 theo đúng thứ tự: giới hạn ngày → embed truy vấn → cache (BR-24) → Top-K (BR-8) → ngưỡng → LLM → kiểm tra →
 * lọc nội dung (BR-15) → lưu cache (BR-25) → ghi lần phát. Mọi sự cố của AI trả {@code NeedsFallback} (BR-7, PER-4);
 * lỗi hạ tầng khác (DB, cấu hình thiếu) vẫn ném ra như bình thường.
 */
@Service
public class GenerateWritingPromptService implements GenerateWritingPromptUseCase {

    static final String TEMPLATE = "rag_question_generation";
    static final String DAILY_LIMIT_SETTING = "writing.ai_prompt_daily_limit";
    static final String TOP_K_SETTING = "rag.retrieval.top_k";
    static final String RETRIEVAL_MIN_SETTING = "rag.retrieval.min_similarity";
    static final String CACHE_MIN_SETTING = "rag.cache.min_similarity";
    static final String CACHE_DEDUPE_DAYS_SETTING = "rag.cache.dedupe_days";
    static final String CACHE_TTL_DAYS_SETTING = "rag.cache.ttl_days";
    static final String MAX_BANK_SIMILARITY_SETTING = "rag.generation.max_bank_similarity";

    private static final Logger log = LoggerFactory.getLogger(GenerateWritingPromptService.class);

    private final LearnerContextPort learners;
    private final PromptCachePort cache;
    private final WritingPromptVectorPort vectors;
    private final PromptExampleDetailsPort exampleDetails;
    private final GenerationLogPort generationLog;
    private final EmbeddingPort embedding;
    private final LlmPort llm;
    private final ContentFilterPort contentFilter;
    private final SettingsPort settings;
    private final ClockPort clock;
    private final IdGeneratorPort ids;

    public GenerateWritingPromptService(LearnerContextPort learners, PromptCachePort cache,
            WritingPromptVectorPort vectors, PromptExampleDetailsPort exampleDetails, GenerationLogPort generationLog,
            EmbeddingPort embedding, LlmPort llm, ContentFilterPort contentFilter, SettingsPort settings,
            ClockPort clock, IdGeneratorPort ids) {
        this.learners = learners;
        this.cache = cache;
        this.vectors = vectors;
        this.exampleDetails = exampleDetails;
        this.generationLog = generationLog;
        this.embedding = embedding;
        this.llm = llm;
        this.contentFilter = contentFilter;
        this.settings = settings;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public GenerationResult generate(UUID userId, GenerateCommand command) {
        Optional<WritingTaskType> requestedType = parseRequestedType(command.taskType());
        Instant start = clock.now();
        Attempt attempt = new Attempt();
        LatencyBudget budget = new LatencyBudget(command.deadline());
        GenerationResult result;
        FallbackReason fallbackReason = null;
        try {
            result = run(userId, command, requestedType, budget, attempt);
        } catch (Fallback fallback) {
            fallbackReason = fallback.reason;
            result = NeedsFallback.of(fallback.reason);
        }
        record(userId, command, result, fallbackReason, attempt, start);
        return result;
    }

    private GenerationResult run(UUID userId, GenerateCommand command, Optional<WritingTaskType> requestedType,
            LatencyBudget budget, Attempt attempt) {
        LearnerContext learner = learners.context(userId);

        // RAG.Limit.Daily: đếm đề AI đã phát trong ngày giờ địa phương của learner; đề dự phòng từ kho không tính
        Instant now = clock.now();
        LocalDate today = clock.today(learner.zone());
        ZoneId zone = learner.zone();
        int dailyLimit = settings.requiredInt(DAILY_LIMIT_SETTING);
        if (cache.countServedBetween(userId, today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant()) >= dailyLimit) {
            throw new Fallback(FallbackReason.DAILY_LIMIT);
        }

        // RAG.Embed.Input: hàm dựng truy vấn duy nhất
        String query = RagQueryBuilder.build(command.topic(), command.cefrLevel(), learner.interests());
        float[] queryVector = embed(query, TaskType.RETRIEVAL_QUERY, budget);

        // RAG.Cache.Lookup (BR-24)
        Optional<ServedPrompt> hit = cache.findHit(new CacheLookup(queryVector, command.topic(), command.cefrLevel(),
                requestedType.map(WritingTaskType::dbValue), settings.requiredDouble(CACHE_MIN_SETTING), userId,
                now.minus(Duration.ofDays(settings.requiredInt(CACHE_DEDUPE_DAYS_SETTING))), now));
        if (hit.isPresent()) {
            cache.serveHit(hit.get().cacheId(), userId, now);
            return new Generated(hit.get(), Source.CACHE_HIT);
        }

        // RAG.Retrieve.Search (BR-8): chỉ gọi LLM khi Top-1 đủ gần
        List<SimilarWritingPrompt> top = vectors.nearest(queryVector, command.topic(), command.cefrLevel(),
                settings.requiredInt(TOP_K_SETTING));
        Optional<Double> top1 = top.stream().findFirst().map(SimilarWritingPrompt::similarity);
        attempt.top1Similarity = top1.orElse(null);
        if (!RagDecisionPolicy.meetsRetrievalThreshold(top1, settings.requiredDouble(RETRIEVAL_MIN_SETTING))) {
            throw new Fallback(FallbackReason.BELOW_THRESHOLD);
        }

        // RAG.Generate.Prompt
        List<PromptExample> examples = examples(top);
        Map<String, String> vars = new HashMap<>();
        vars.put("topic", command.topic());
        vars.put("level", command.cefrLevel());
        vars.put("interests", RagQueryBuilder.interestsText(learner.interests()));
        vars.put("task_type_rule", PromptExamples.taskTypeRule(requestedType));
        vars.put("examples", PromptExamples.render(examples));
        GeneratedPrompt generated = callLlm(vars, budget);

        // RAG.Generate.Validate: kiểm tra rẻ trước (cấu trúc, từ cấm), kiểm tra tốn thêm một lần embedding sau cùng
        if (RagDecisionPolicy.validate(generated, PromptExamples.allowedTypes(requestedType, examples),
                PromptExamples.wordRange(examples)).isPresent()) {
            throw new Fallback(FallbackReason.INVALID_OUTPUT);
        }
        if (!contentFilter.check(generated.promptText()).allowed()) {
            throw new Fallback(FallbackReason.FILTERED);
        }
        float[] promptVector = embed(generated.promptText(), TaskType.RETRIEVAL_DOCUMENT, budget);
        Optional<Double> closestBank = vectors.nearest(promptVector, command.topic(), command.cefrLevel(), 1).stream()
                .findFirst().map(SimilarWritingPrompt::similarity);
        if (RagDecisionPolicy.checkBankSimilarity(closestBank, settings.requiredDouble(MAX_BANK_SIMILARITY_SETTING))
                .isPresent()) {
            throw new Fallback(FallbackReason.INVALID_OUTPUT);
        }

        // BR-25: lưu cache kèm embedding truy vấn và Top-1; ghi lần phát đầu tiên
        Instant stored = clock.now();
        UUID cacheId = ids.newId();
        String taskType = WritingTaskType.parse(generated.taskType()).orElseThrow().dbValue();
        cache.storeAndServe(new NewCacheEntry(cacheId, queryVector, command.topic(), command.cefrLevel(), taskType,
                generated, top.get(0).questionId(),
                stored.plus(Duration.ofDays(settings.requiredInt(CACHE_TTL_DAYS_SETTING)))), userId, stored);
        return new Generated(new ServedPrompt(cacheId, generated.promptText().strip(), taskType, generated.minWords(),
                generated.maxWords()), Source.GENERATED);
    }

    private float[] embed(String text, TaskType type, LatencyBudget budget) {
        ensureTimeLeft(budget);
        try {
            return embedding.embed(text, type);
        } catch (AiCallException e) {
            throw new Fallback(e.kind() == AiCallException.Kind.TIMEOUT ? FallbackReason.TIMEOUT
                    : FallbackReason.NO_EMBEDDING);
        }
    }

    /** LLM chỉ được chờ trong thời gian còn lại của ngân sách (ai.question_gen_p95_budget_ms). */
    private GeneratedPrompt callLlm(Map<String, String> vars, LatencyBudget budget) {
        ensureTimeLeft(budget);
        try {
            LlmResult<GeneratedPromptResponse> result = llm.generateJson(TEMPLATE, vars, GeneratedPromptResponse.class,
                    budget.remaining(clock.now()));
            return result.value().toDomain();
        } catch (AiCallException e) {
            throw new Fallback(switch (e.kind()) {
                case TIMEOUT -> FallbackReason.TIMEOUT;
                case INVALID_JSON -> FallbackReason.INVALID_JSON;
                case SAFETY_BLOCKED -> FallbackReason.SAFETY_BLOCKED;
                case RATE_LIMITED, UPSTREAM_ERROR -> FallbackReason.UPSTREAM_ERROR;
            });
        }
    }

    private void ensureTimeLeft(LatencyBudget budget) {
        if (budget.isExpired(clock.now())) {
            throw new Fallback(FallbackReason.TIMEOUT);
        }
    }

    private List<PromptExample> examples(List<SimilarWritingPrompt> top) {
        Map<UUID, ExampleDetails> details = exampleDetails.details(top.stream().map(SimilarWritingPrompt::questionId)
                .toList());
        return top.stream().map(prompt -> {
            ExampleDetails d = details.get(prompt.questionId());
            return d == null ? new PromptExample(prompt.questionText(), null, null, null)
                    : new PromptExample(prompt.questionText(), d.taskType(), d.minWords(), d.maxWords());
        }).toList();
    }

    private static Optional<WritingTaskType> parseRequestedType(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(WritingTaskType.parse(taskType).orElseThrow(() -> new ValidationFailedException(
                "INVALID_TASK_TYPE", "taskType must be email, paragraph or essay", null)));
    }

    /** Ghi nhật ký; lỗi ghi log không được làm hỏng việc phát đề. Không ghi nội dung đề, truy vấn hay sở thích. */
    private void record(UUID userId, GenerateCommand command, GenerationResult result, FallbackReason reason,
            Attempt attempt, Instant start) {
        try {
            int latency = (int) Math.max(0, Duration.between(start, clock.now()).toMillis());
            String outcome = result instanceof Generated generated
                    ? (generated.source() == Source.CACHE_HIT ? "cache_hit" : "generated")
                    : "fallback";
            generationLog.record(new GenerationLogPort.Entry(userId, command.topic(), command.cefrLevel(), outcome,
                    reason, attempt.top1Similarity, latency, clock.now()));
        } catch (RuntimeException e) {
            log.warn("Could not record RAG generation log: {}", e.toString());
        }
    }

    /** Trạng thái một lần chạy, chỉ để ghi nhật ký. */
    private static final class Attempt {
        Double top1Similarity;
    }

    /** Dừng chuỗi sinh đề và chuyển sang đề có sẵn. Không có stack trace: chỉ là điều khiển luồng. */
    private static final class Fallback extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient FallbackReason reason;

        Fallback(FallbackReason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }
    }
}
