package com.pela.rag.domain;

/** Đề Writing do LLM sinh ra theo response_schema của prompt rag_question_generation (đã parse, chưa kiểm tra). */
public record GeneratedPrompt(String promptText, String taskType, int minWords, int maxWords) {
}
