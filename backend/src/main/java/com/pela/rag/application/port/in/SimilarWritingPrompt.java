package com.pela.rag.application.port.in;

import java.util.UUID;

/** Một đề Writing đang dùng và độ tương đồng cosine với truy vấn: similarity = 1 - cosine distance. */
public record SimilarWritingPrompt(UUID questionId, String questionText, String topic, String cefrLevel,
        double similarity) {
}
