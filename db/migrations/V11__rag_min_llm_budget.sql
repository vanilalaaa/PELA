-- V11: UC-24 không gọi LLM khi ngân sách thời gian còn quá ít (BUDGET_EXHAUSTED).
-- Đo ngày 03/11/2026 cho thấy 13/20 lượt gọi Gemini chỉ còn khoảng 0,7 s nên chắc chắn quá hạn, vẫn tốn hạn mức (RPD)
-- và góp phần gây lỗi 429.

INSERT INTO system_setting (setting_key, value, description) VALUES
 ('rag.generation.min_llm_ms', '2000',
  'RAG.Generate: thời gian còn lại tối thiểu (ms) của ngân sách ai.question_gen_p95_budget_ms để còn gọi LLM; ít hơn thì phát đề có sẵn ngay (BUDGET_EXHAUSTED, BR-7) và không tốn hạn mức Gemini')
ON CONFLICT (setting_key) DO NOTHING;

-- Thêm lý do BUDGET_EXHAUSTED vào nhật ký sinh đề (ràng buộc cũ không có giá trị này)
ALTER TABLE rag_generation_log DROP CONSTRAINT rag_generation_log_reason_check;
ALTER TABLE rag_generation_log ADD CONSTRAINT rag_generation_log_reason_check
    CHECK (reason IN ('BELOW_THRESHOLD', 'TIMEOUT', 'INVALID_JSON', 'SAFETY_BLOCKED', 'FILTERED', 'DAILY_LIMIT',
                      'NO_EMBEDDING', 'INVALID_OUTPUT', 'UPSTREAM_ERROR', 'BUDGET_EXHAUSTED'));
