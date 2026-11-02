package com.pela.rag.application.usecase;

import com.pela.rag.application.port.in.FindDuplicateWritingPromptsUseCase;
import com.pela.rag.application.port.in.SimilarWritingPrompt;
import com.pela.rag.application.port.out.WritingPromptVectorPort;
import com.pela.shared.application.port.out.EmbeddingPort;
import com.pela.shared.application.port.out.EmbeddingPort.TaskType;
import com.pela.shared.application.port.out.SettingsPort;
import com.pela.shared.domain.vector.VectorMath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Admin.QuestionBank.Duplicates: cảnh báo đề Writing sắp nhập giống đề đang dùng hoặc giống đề khác trong file. */
@Service
public class DuplicatePromptService implements FindDuplicateWritingPromptsUseCase {

    static final String THRESHOLD_SETTING = "import.duplicate_similarity";
    static final String MAX_RESULTS_SETTING = "import.duplicate_max_results";

    private final WritingPromptVectorPort prompts;
    private final EmbeddingPort embedding;
    private final SettingsPort settings;

    public DuplicatePromptService(WritingPromptVectorPort prompts, EmbeddingPort embedding, SettingsPort settings) {
        this.prompts = prompts;
        this.embedding = embedding;
        this.settings = settings;
    }

    @Override
    public List<SimilarWritingPrompt> duplicates(String promptText, UUID excludeQuestionId) {
        return existingDuplicates(embed(promptText), excludeQuestionId);
    }

    @Override
    public List<DuplicateWarning> checkImport(List<NewPrompt> newPrompts) {
        double threshold = settings.requiredDouble(THRESHOLD_SETTING);
        List<float[]> vectors = newPrompts.stream().map(p -> embed(p.text())).toList();
        List<DuplicateWarning> warnings = new ArrayList<>();
        for (int i = 0; i < newPrompts.size(); i++) {
            List<InFileDuplicate> inFile = new ArrayList<>();
            for (int j = 0; j < newPrompts.size(); j++) {
                if (j == i) {
                    continue;
                }
                double similarity = VectorMath.cosineSimilarity(vectors.get(i), vectors.get(j));
                if (similarity >= threshold) {
                    inFile.add(new InFileDuplicate(j, newPrompts.get(j).topic(), newPrompts.get(j).cefrLevel(),
                            similarity));
                }
            }
            inFile.sort(Comparator.comparingDouble(InFileDuplicate::similarity).reversed()
                    .thenComparingInt(InFileDuplicate::otherIndex));
            List<SimilarWritingPrompt> existing = existingDuplicates(vectors.get(i), null);
            if (!existing.isEmpty() || !inFile.isEmpty()) {
                warnings.add(new DuplicateWarning(i, existing, inFile));
            }
        }
        return warnings;
    }

    private List<SimilarWritingPrompt> existingDuplicates(float[] vector, UUID excludeQuestionId) {
        return prompts.similarAtLeast(vector, settings.requiredDouble(THRESHOLD_SETTING),
                settings.requiredInt(MAX_RESULTS_SETTING), excludeQuestionId);
    }

    /** Đề so với đề (cùng loại văn bản) nên dùng RETRIEVAL_DOCUMENT như lúc lưu. */
    private float[] embed(String text) {
        return embedding.embed(text, TaskType.RETRIEVAL_DOCUMENT);
    }
}
