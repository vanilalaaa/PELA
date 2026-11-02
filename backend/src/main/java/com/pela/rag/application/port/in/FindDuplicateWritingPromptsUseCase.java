package com.pela.rag.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Admin.QuestionBank.Duplicates: cảnh báo đề Writing giống nhau ≥ {@code import.duplicate_similarity}. So với mọi đề
 * Writing đang dùng (mọi topic/level, không tính đề retired) và giữa các đề mới trong cùng một file nhập.
 */
public interface FindDuplicateWritingPromptsUseCase {

    /** @param excludeQuestionId đề đang sửa (không tự so với chính nó); null khi nhập đề mới */
    List<SimilarWritingPrompt> duplicates(String promptText, UUID excludeQuestionId);

    /**
     * Kiểm tra các đề trong một file nhập; chỉ trả cảnh báo cho đề có ít nhất một đề trùng.
     *
     * @param prompts thứ tự như trong file; {@link DuplicateWarning#promptIndex()} trỏ vào danh sách này
     */
    List<DuplicateWarning> checkImport(List<NewPrompt> prompts);

    record NewPrompt(String text, String topic, String cefrLevel) {
    }

    /**
     * @param existing đề đang dùng trùng với đề mới (question_id, topic, cefr_level, similarity)
     * @param inFile   đề mới khác trong cùng file trùng với đề này
     */
    record DuplicateWarning(int promptIndex, List<SimilarWritingPrompt> existing, List<InFileDuplicate> inFile) {
    }

    /** @param otherIndex vị trí của đề trùng trong cùng file */
    record InFileDuplicate(int otherIndex, String topic, String cefrLevel, double similarity) {
    }
}
