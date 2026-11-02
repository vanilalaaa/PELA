-- V5: chọn model TTS cho Listening (BR-37, ADR 0005). V3 để trống; không sửa V3.
-- Chỉ ghi khi giá trị còn rỗng để không đè lựa chọn Admin đã đổi.
UPDATE system_setting
SET value       = '"gemini-3.8-flash-tts"',
    description = 'BR-37, MNT-3: model TTS sinh audio Listening. Phương án dự phòng: gemini-3.8-flash-tts-lite (đổi giá trị này, không cần sửa code)',
    updated_at  = now()
WHERE setting_key = 'ai.model.tts'
  AND value = '""'::jsonb;
