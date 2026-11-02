package com.pela.rag.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pela.rag.application.port.in.BackfillPromptEmbeddingsUseCase.BackfillResult;
import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.rag.application.port.out.WritingPromptVectorPort;
import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.NotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromptEmbeddingServiceTest {

    private static final Map<String, String> SETTINGS = Map.of(
            "ai.model.embedding", "\"gemini-embedding-001\"",
            "rag.embedding.backfill_batch_size", "2",
            "rag.embedding.backfill_pause_ms", "1500");

    /** Bộ nhớ thay question_bank: id → (text, pendingActivation); đề đã xử lý thì bị xoá khỏi danh sách cần làm. */
    static final class FakePrompts implements WritingPromptVectorPort {
        final Map<UUID, WritingPromptVectorPort.BackfillCandidate> todo = new LinkedHashMap<>();
        final List<String> stored = new ArrayList<>();

        UUID add(String text, boolean pending) {
            UUID id = UUID.randomUUID();
            todo.put(id, new BackfillCandidate(id, text, pending));
            return id;
        }

        @Override
        public List<SimilarWritingPrompt> nearest(float[] q, String topic, String level, int k) {
            return List.of();
        }

        @Override
        public List<SimilarWritingPrompt> similarAtLeast(float[] q, double min, int limit, UUID exclude) {
            return List.of();
        }

        @Override
        public Optional<String> promptText(UUID id) {
            return Optional.ofNullable(todo.get(id)).map(BackfillCandidate::questionText);
        }

        @Override
        public boolean storeEmbedding(UUID id, float[] v, String model) {
            stored.add("reembed:" + todo.remove(id).questionText() + ":" + model);
            return true;
        }

        @Override
        public List<BackfillCandidate> backfillCandidates(String model, int limit, Collection<UUID> skip) {
            return todo.values().stream().filter(c -> !skip.contains(c.questionId())).limit(limit).toList();
        }

        @Override
        public boolean storeEmbeddingAndActivate(UUID id, float[] v, String model) {
            stored.add("activate:" + todo.remove(id).questionText());
            return true;
        }
    }

    private final FakePrompts prompts = new FakePrompts();
    private final List<Duration> pauses = new ArrayList<>();
    private final SettingsPort settings = key -> Optional.ofNullable(SETTINGS.get(key));

    private PromptEmbeddingService service(EmbeddingPort embedding) {
        return new PromptEmbeddingService(prompts, embedding, settings, pauses::add);
    }

    @Test
    void processesInBatchesWithPauseBetweenBatchesOnly() {
        prompts.add("a", true);
        prompts.add("b", false);
        prompts.add("c", true);

        BackfillResult result = service((text, type) -> new float[] {1}).backfill();

        assertThat(result).isEqualTo(new BackfillResult(1, 2, 0, false));
        assertThat(prompts.stored).containsExactly("activate:a", "reembed:b:gemini-embedding-001", "activate:c");
        assertThat(pauses).containsExactly(Duration.ofMillis(1500));
    }

    @Test
    void failedPromptIsSkippedAndRunEnds() {
        prompts.add("bad", true);
        prompts.add("good", true);

        BackfillResult result = service((text, type) -> {
            if (text.equals("bad")) {
                throw new AiCallException(Kind.UPSTREAM_ERROR, "503");
            }
            return new float[] {1};
        }).backfill();

        assertThat(result).isEqualTo(new BackfillResult(0, 1, 1, false));
        assertThat(prompts.todo).hasSize(1);
    }

    @Test
    void rateLimitStopsTheRun() {
        prompts.add("a", true);
        prompts.add("b", true);

        BackfillResult result = service((text, type) -> {
            throw new AiCallException(Kind.RATE_LIMITED, "429");
        }).backfill();

        assertThat(result.stoppedByRateLimit()).isTrue();
        assertThat(prompts.todo).hasSize(2);
    }

    @Test
    void embedForStorageReturnsConfiguredModel() {
        var e = service((text, type) -> new float[] {0.5f}).embedForStorage("Describe your town.");

        assertThat(e.model()).isEqualTo("gemini-embedding-001");
        assertThat(e.vector()).containsExactly(0.5f);
    }

    @Test
    void reembedUnknownPromptIsNotFound() {
        assertThatThrownBy(() -> service((t, k) -> new float[] {1}).reembed(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
