-- V12: cấu hình mức "thinking" của Gemini theo từng prompt (MNT-4), không hard-code trong code.
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
