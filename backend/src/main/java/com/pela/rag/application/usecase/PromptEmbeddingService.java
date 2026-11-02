package com.pela.rag.application.usecase;

import com.pela.rag.application.port.in.BackfillPromptEmbeddingsUseCase;
import com.pela.rag.application.port.in.EmbedWritingPromptUseCase;
import com.pela.rag.application.port.out.PacingPort;
import com.pela.rag.application.port.out.WritingPromptVectorPort;
import com.pela.rag.application.port.out.WritingPromptVectorPort.BackfillCandidate;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.NotFoundException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** BR-14: embedding đề Writing khi Admin lưu/sửa, và tạo bù theo lô (EmbedMissingPromptsJob). */
@Service
public class PromptEmbeddingService implements EmbedWritingPromptUseCase, BackfillPromptEmbeddingsUseCase {

    static final String MODEL_SETTING = "ai.model.embedding";
    static final String BATCH_SIZE_SETTING = "rag.embedding.backfill_batch_size";
    static final String PAUSE_SETTING = "rag.embedding.backfill_pause_ms";

    private static final Logger log = LoggerFactory.getLogger(PromptEmbeddingService.class);

    private final WritingPromptVectorPort prompts;
    private final EmbeddingPort embedding;
    private final SettingsPort settings;
    private final PacingPort pacing;

    public PromptEmbeddingService(WritingPromptVectorPort prompts, EmbeddingPort embedding, SettingsPort settings,
            PacingPort pacing) {
        this.prompts = prompts;
        this.embedding = embedding;
        this.settings = settings;
        this.pacing = pacing;
    }

    @Override
    public PromptEmbedding embedForStorage(String promptText) {
        String model = settings.requiredString(MODEL_SETTING);
        return new PromptEmbedding(embedding.embed(promptText, TaskType.RETRIEVAL_DOCUMENT), model);
    }

    @Override
    public void reembed(UUID questionId) {
        String text = prompts.promptText(questionId)
                .orElseThrow(() -> new NotFoundException("PROMPT_NOT_FOUND", "Writing prompt not found", null));
        PromptEmbedding e = embedForStorage(text);
        prompts.storeEmbedding(questionId, e.vector(), e.model());
    }

    @Override
    public BackfillResult backfill() {
        String model = settings.requiredString(MODEL_SETTING);
        int batchSize = settings.requiredInt(BATCH_SIZE_SETTING);
        Duration pause = Duration.ofMillis(settings.requiredInt(PAUSE_SETTING));
        Set<UUID> attempted = new HashSet<>();
        int reembedded = 0;
        int activated = 0;
        int failed = 0;
        boolean first = true;
        while (true) {
            List<BackfillCandidate> batch = prompts.backfillCandidates(model, batchSize, attempted);
            if (batch.isEmpty()) {
                break;
            }
            if (!first) {
                pacing.pause(pause);
            }
            first = false;
            for (BackfillCandidate c : batch) {
                attempted.add(c.questionId());
                float[] vector;
                try {
                    vector = embedding.embed(c.questionText(), TaskType.RETRIEVAL_DOCUMENT);
                } catch (AiCallException e) {
                    if (e.kind() == AiCallException.Kind.RATE_LIMITED) {
                        log.warn("Prompt embedding backfill stopped by rate limit: reembedded={}, activated={}",
                                reembedded, activated);
                        return new BackfillResult(reembedded, activated, failed, true);
                    }
                    failed++;
                    log.warn("Prompt embedding failed: question={}, kind={}", c.questionId(), e.kind());
                    continue;
                }
                if (c.pendingActivation()) {
                    activated += prompts.storeEmbeddingAndActivate(c.questionId(), vector, model) ? 1 : 0;
                } else {
                    reembedded += prompts.storeEmbedding(c.questionId(), vector, model) ? 1 : 0;
                }
            }
        }
        log.info("Prompt embedding backfill done: reembedded={}, activated={}, failed={}", reembedded, activated, failed);
        return new BackfillResult(reembedded, activated, failed, false);
    }
}
