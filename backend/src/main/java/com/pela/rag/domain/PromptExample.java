package com.pela.rag.domain;

/**
 * Một đề mẫu (đề kho cùng topic + level) đưa vào prompt LLM.
 *
 * @param taskType dạng bài (email|paragraph|essay); null nếu đề kho không ghi
 * @param minWords payload.min_words; null nếu không ghi
 * @param maxWords payload.max_words; null nếu không ghi
 */
public record PromptExample(String text, String taskType, Integer minWords, Integer maxWords) {

    /** Dòng đưa vào biến {{examples}}: "1. [email, 20-40 words] Write an email…". */
    String render(int number) {
        String type = taskType == null ? "unknown type" : taskType;
        String range = minWords != null && maxWords != null ? ", " + minWords + "-" + maxWords + " words" : "";
        return number + ". [" + type + range + "] " + text.strip();
    }
}
