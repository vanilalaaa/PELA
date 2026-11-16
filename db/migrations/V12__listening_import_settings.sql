-- V12: nhập Listening từ CSV + luyện nghe (UC-11, UC-19, BR-37, ADR 0005). Không sửa migration cũ.
-- Bucket Storage 'imports' và 'listening-audio' (riêng tư) tạo trên Supabase Dashboard; Flyway không quản lý schema storage.

INSERT INTO system_setting (setting_key, value, description) VALUES
 ('listening.audio_url_ttl_sec', '3600', 'UC-11, UC-19: số giây hiệu lực của URL ký tạm cho audio bài nghe (learner nghe, Admin nghe thử)')
ON CONFLICT (setting_key) DO NOTHING;

UPDATE system_setting
SET description = 'ADMIN E/F: dung lượng tối đa (MB) của file CSV nhập (lộ trình, Listening)',
    updated_at  = now()
WHERE setting_key = 'import.max_csv_mb';
