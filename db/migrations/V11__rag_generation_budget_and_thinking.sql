-- V11: UC-24 giữ trong ngân sách thời gian và tiết kiệm hạn mức Gemini (sau lần đo độ trễ 03/11/2026).
--   1. Không gọi LLM khi ngân sách còn quá ít (BUDGET_EXHAUSTED).
--   2. Mức "thinking" của Gemini cấu hình theo từng prompt (MNT-4), không hard-code trong code.
-- Không sửa V10 (đã merge và đã áp lên Supabase dev).

-- ---------------------------------------------------------------------
-- 1. rag.generation.min_llm_ms + lý do BUDGET_EXHAUSTED
-- ---------------------------------------------------------------------
-- Đo ngày 03/11/2026: 13/20 lượt gọi Gemini chỉ còn khoảng 0,7 s nên chắc chắn quá hạn, vẫn tốn hạn mức (RPD) và góp phần
-- gây lỗi 429.
INSERT INTO system_setting (setting_key, value, description) VALUES
 ('rag.generation.min_llm_ms', '2000',
  'RAG.Generate: thời gian còn lại tối thiểu (ms) của ngân sách ai.question_gen_p95_budget_ms để còn gọi LLM; ít hơn thì phát đề có sẵn ngay (BUDGET_EXHAUSTED, BR-7) và không tốn hạn mức Gemini')
ON CONFLICT (setting_key) DO NOTHING;

-- Thêm lý do BUDGET_EXHAUSTED vào nhật ký sinh đề (ràng buộc cũ của V10 không có giá trị này)
ALTER TABLE rag_generation_log DROP CONSTRAINT rag_generation_log_reason_check;
ALTER TABLE rag_generation_log ADD CONSTRAINT rag_generation_log_reason_check
    CHECK (reason IN ('BELOW_THRESHOLD', 'TIMEOUT', 'INVALID_JSON', 'SAFETY_BLOCKED', 'FILTERED', 'DAILY_LIMIT',
                      'NO_EMBEDDING', 'INVALID_OUTPUT', 'UPSTREAM_ERROR', 'BUDGET_EXHAUSTED'));

-- ---------------------------------------------------------------------
-- 2. prompt_template.thinking_level
-- ---------------------------------------------------------------------
-- Nguồn: Gemini API generateContent, generationConfig.thinkingConfig.thinkingLevel (MINIMAL | LOW | MEDIUM | HIGH),
--   https://aistudio.google.com/api-reference/generate-content ; bảng mức theo model: https://aistudio.google.com/docs/thinking
--   gemini-3.8-flash (ai.model.text từ V6) mặc định MEDIUM và chỉ có LOW | MEDIUM | HIGH (không có MINIMAL).
-- NULL = không gửi thinkingConfig (model tự dùng mặc định). Đặt mức cho model không hỗ trợ sẽ bị Gemini trả lỗi, nên khi đổi
-- ai.model.text phải kiểm tra lại mức này.
ALTER TABLE prompt_template
    ADD COLUMN thinking_level varchar(10) CHECK (thinking_level IN ('MINIMAL', 'LOW', 'MEDIUM', 'HIGH'));
COMMENT ON COLUMN prompt_template.thinking_level IS 'generationConfig.thinkingConfig.thinkingLevel; NULL = mặc định của model';

-- Tạo một đề Writing ngắn không cần suy nghĩ sâu: dùng mức thấp nhất mà gemini-3.8-flash hỗ trợ để giảm độ trễ
UPDATE prompt_template SET thinking_level = 'LOW'
WHERE name = 'rag_question_generation' AND version = 2;
