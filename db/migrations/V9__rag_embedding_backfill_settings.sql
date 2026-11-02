-- V9: cấu hình tạo bù embedding đề Writing (EmbedMissingPromptsJob) và phát hiện đề trùng (Admin.QuestionBank.Duplicates).
INSERT INTO system_setting (setting_key, value, description) VALUES
 ('rag.embedding.backfill_batch_size', '20',   'Số đề Writing embed mỗi lô khi tạo bù (BR-14)'),
 ('rag.embedding.backfill_pause_ms',   '2000', 'Nghỉ giữa các lô embed để tránh rate limit Gemini'),
 ('import.duplicate_max_results',      '5',    'Admin.QuestionBank.Duplicates: số đề tương tự tối đa trả về cho mỗi đề nhập')
ON CONFLICT (setting_key) DO NOTHING;
