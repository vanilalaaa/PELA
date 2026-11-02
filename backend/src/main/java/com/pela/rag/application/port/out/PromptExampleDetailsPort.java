package com.pela.rag.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Dạng bài và khoảng từ của các đề kho (question_bank) dùng làm đề mẫu trong prompt. */
public interface PromptExampleDetailsPort {

    /** Id không có trong kho thì không xuất hiện trong kết quả. */
    Map<UUID, ExampleDetails> details(Collection<UUID> questionIds);

    /** minWords / maxWords null nếu payload không ghi. */
    record ExampleDetails(String taskType, Integer minWords, Integer maxWords) {
    }
}
