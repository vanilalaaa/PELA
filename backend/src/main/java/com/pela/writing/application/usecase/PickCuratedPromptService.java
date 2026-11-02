package com.pela.writing.application.usecase;

import com.pela.writing.application.port.in.PickCuratedPromptUseCase;
import com.pela.writing.application.port.out.CuratedPromptPort;
import com.pela.writing.domain.CuratedPromptPolicy;
import com.pela.writing.domain.PromptCandidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** BR-7 (RAG.Fallback.OnFailure): đề có sẵn cùng topic + level; luật chọn nằm ở {@link CuratedPromptPolicy}. */
@Service
public class PickCuratedPromptService implements PickCuratedPromptUseCase {

    private final CuratedPromptPort prompts;
    private final RandomGenerator random;

    @Autowired
    public PickCuratedPromptService(CuratedPromptPort prompts) {
        this(prompts, RandomGenerator.getDefault());
    }

    PickCuratedPromptService(CuratedPromptPort prompts, RandomGenerator random) {
        this.prompts = prompts;
        this.random = random;
    }

    @Override
    public Optional<CuratedPrompt> pick(UUID userId, String topic, String cefrLevel) {
        return CuratedPromptPolicy.choose(prompts.candidates(userId, topic, cefrLevel), random::nextInt)
                .map(PickCuratedPromptService::toView);
    }

    @Override
    public List<CuratedPrompt> suggestions(UUID userId, String topic, String cefrLevel, int limit) {
        return CuratedPromptPolicy.suggestions(prompts.candidates(userId, topic, cefrLevel), limit).stream()
                .map(PickCuratedPromptService::toView).toList();
    }

    private static CuratedPrompt toView(PromptCandidate c) {
        return new CuratedPrompt(c.questionId(), c.text(), c.taskType(), c.minWords(), c.maxWords(), c.done());
    }
}
