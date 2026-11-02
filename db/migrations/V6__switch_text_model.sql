-- V6: đổi model sinh văn bản sang gemini-3.8-flash (MNT-3, ADR 0001).
-- Gọi thật (01/11/2026): gemini-2.5-flash trả 404 "no longer available to new users", Gemini gợi ý gemini-3.8-flash.
-- Chỉ đổi khi giá trị còn là model cũ để không đè lựa chọn Admin đã đổi. Không sửa V3/V5.
UPDATE system_setting
SET value       = '"gemini-3.8-flash"',
    description = 'MNT-3: model sinh văn bản (chấm Writing, tạo đề, xử lý file nhập); gọi qua generateContent (ADR 0001)',
    updated_at  = now()
WHERE setting_key = 'ai.model.text'
  AND value = '"gemini-2.5-flash"'::jsonb;

-- Sửa tên model TTS dự phòng trong mô tả của V5 theo trang Speech generation: gemini-3.8-flash-lite-tts.
UPDATE system_setting
SET description = 'BR-37, MNT-3: model TTS sinh audio Listening. Phương án dự phòng: gemini-3.8-flash-lite-tts (đổi giá trị này, không cần sửa code)',
    updated_at  = now()
WHERE setting_key = 'ai.model.tts';
