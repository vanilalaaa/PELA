package com.pela.writing.application.usecase;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.rag.application.port.in.GenerateWritingPromptUseCase;
import com.pela.rag.application.port.in.GenerateWritingPromptUseCase.GenerateCommand;
import com.pela.rag.application.port.in.GenerationResult;
import com.pela.rag.application.port.in.GenerationResult.Generated;
import com.pela.rag.application.port.in.GenerationResult.NeedsFallback;
import com.pela.rag.application.port.in.GenerationResult.Source;
import com.pela.rag.application.port.in.ServedPrompt;
import com.pela.shared.application.port.out.ClockPort;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.ValidationFailedException;
import com.pela.writing.application.port.in.PickCuratedPromptUseCase;
import com.pela.writing.application.port.in.PickCuratedPromptUseCase.CuratedPrompt;
import com.pela.writing.application.port.in.WritingPromptUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Điều phối UC-24 phía writing: gọi rag (không phụ thuộc ngược), nếu rag báo cần dự phòng thì chọn đề có sẵn (BR-7).
 * Ngân sách thời gian ai.question_gen_p95_budget_ms tính cho cả chuỗi.
 */
@Service
public class WritingPromptService implements WritingPromptUseCase {

    static final String BUDGET_SETTING = "ai.question_gen_p95_budget_ms";
    static final int MAX_SUGGESTIONS = 20;
    private static final int MAX_TOPIC_LENGTH = 100;

    private final GenerateWritingPromptUseCase rag;
    private final PickCuratedPromptUseCase curated;
    private final GetCurrentUserUseCase users;
    private final SettingsPort settings;
    private final ClockPort clock;

    public WritingPromptService(GenerateWritingPromptUseCase rag, PickCuratedPromptUseCase curated,
            GetCurrentUserUseCase users, SettingsPort settings, ClockPort clock) {
        this.rag = rag;
        this.curated = curated;
        this.users = users;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public PromptChoice generate(UUID userId, String topic, String taskType) {
        String cleanTopic = cleanTopic(topic);
        String level = levelOf(userId);
        Instant deadline = clock.now().plusMillis(settings.requiredInt(BUDGET_SETTING));

        GenerationResult result = rag.generate(userId, new GenerateCommand(cleanTopic, level, taskType, deadline));
        if (result instanceof Generated generated) {
            ServedPrompt prompt = generated.prompt();
            return new PromptChoice("ai_generated", prompt.cacheId(), null, prompt.promptText(), prompt.taskType(),
                    prompt.minWords(), prompt.maxWords(), generated.source() == Source.CACHE_HIT, null);
        }
        String reason = ((NeedsFallback) result).reason();
        CuratedPrompt bank = curated.pick(userId, cleanTopic, level).orElseThrow(() -> new NotFoundException(
                "NO_PROMPT_AVAILABLE", "No Writing prompt is available for this topic and level", null));
        return new PromptChoice("bank", null, bank.questionId(), bank.text(), bank.taskType(), bank.minWords(),
                bank.maxWords(), false, reason);
    }

    @Override
    public List<CuratedPrompt> suggestions(UUID userId, String topic, int limit) {
        return curated.suggestions(userId, cleanTopic(topic), levelOf(userId), Math.min(Math.max(limit, 1), MAX_SUGGESTIONS));
    }

    private String levelOf(UUID userId) {
        String level = users.currentUser(userId).cefrLevel();
        if (level == null || level.isBlank()) {
            throw new ValidationFailedException("LEVEL_REQUIRED", "Choose your CEFR level before requesting a prompt",
                    null);
        }
        return level;
    }

    private static String cleanTopic(String topic) {
        if (topic == null || topic.isBlank() || topic.strip().length() > MAX_TOPIC_LENGTH) {
            throw new ValidationFailedException("VALIDATION_FAILED", "topic is required (at most 100 characters)", null);
        }
        return topic.strip();
    }
}
