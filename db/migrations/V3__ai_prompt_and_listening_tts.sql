-- V3: cấu trúc prompt AI (MNT-4) và Listening chuyển sang TTS (BR-37, ADR 0005).
-- Không sửa V1/V2; mọi thay đổi nằm ở đây.

-- ---------------------------------------------------------------------
-- a. prompt_template: tách system prompt / user template / schema output
-- ---------------------------------------------------------------------
ALTER TABLE prompt_template
    ADD COLUMN system_text     text,            -- system instruction gửi cho model
    ADD COLUMN response_schema jsonb,           -- JSON schema bắt buộc cho output (validate trước khi dùng)
    ADD COLUMN temperature     numeric(3,2),    -- NULL = mặc định của model
    ADD COLUMN note            text;            -- ghi chú thay đổi giữa các version
COMMENT ON COLUMN prompt_template.body IS 'User template; placeholder dạng {{level}}, {{topic}}…';

-- Mỗi name tối đa 1 bản is_active = true: index này đã có từ V1 (uq_prompt_active), giữ nguyên.
CREATE UNIQUE INDEX IF NOT EXISTS uq_prompt_active ON prompt_template(name) WHERE is_active;

-- Prompt v1 giữ chỗ (nội dung thật làm ở P5 / P7 / P9 bằng version mới)
INSERT INTO prompt_template (name, version, body, system_text, response_schema, is_active, note) VALUES
 ('writing_grading',          1, 'TODO v1', 'TODO v1', '{}'::jsonb, true, 'Placeholder - prompt thật ở P5 (UC-23)'),
 ('rag_question_generation',  1, 'TODO v1', 'TODO v1', '{}'::jsonb, true, 'Placeholder - prompt thật ở P7 (UC-24)'),
 ('writing_file_extraction',  1, 'TODO v1', 'TODO v1', '{}'::jsonb, true, 'Placeholder - prompt thật ở P9 (Admin nhập file đề Writing)')
ON CONFLICT (name, version) DO NOTHING;

-- ---------------------------------------------------------------------
-- b. Listening: Admin nhập CSV lời thoại, audio do Gemini TTS sinh khi lưu (BR-37)
-- ---------------------------------------------------------------------
-- Giữ 'listening_audio' để không phá job cũ; luồng mới dùng 'listening_csv'
ALTER TABLE import_job DROP CONSTRAINT import_job_kind_check;
ALTER TABLE import_job ADD CONSTRAINT import_job_kind_check
    CHECK (kind IN ('roadmap_csv', 'listening_audio', 'writing_file', 'listening_csv'));

ALTER TABLE listening_clip
    ADD COLUMN voice_config jsonb NOT NULL DEFAULT '{}'::jsonb,   -- {"<speaker>": "<voice name>"} lúc sinh audio
    ADD COLUMN tts_model    varchar(60),                          -- model TTS đã dùng (re-generate khi đổi model)
    ADD COLUMN audio_format varchar(10);                          -- vd 'wav', 'mp3'

-- ---------------------------------------------------------------------
-- c. Cấu hình AI / Listening / Star (MNT-3: không hard-code)
-- ---------------------------------------------------------------------
INSERT INTO system_setting (setting_key, value, description) VALUES
 ('ai.model.text',            '"gemini-2.5-flash"',      'MNT-3: model sinh văn bản (chấm Writing, tạo đề, xử lý file nhập)'),
 ('ai.model.embedding',       '"gemini-embedding-001"',  'MNT-3: model embedding cho RAG / phát hiện đề trùng'),
 ('ai.embedding.dimensions',  '768',                     'MNT-3: số chiều embedding; đổi thì phải migration cột vector + re-embed'),
 ('ai.model.tts',             '""',                      'BR-37, MNT-3: model TTS sinh audio Listening - chưa chọn, điền trước khi bật luồng import'),
 ('ai.tts.sample_rate',       '24000',                   'BR-37: sample rate (Hz) audio TTS'),
 ('ai.tts.style_by_level',    '{"A1":"Speak slowly and clearly","A2":"Speak slowly and clearly","B1":"Natural pace","B2":"Natural pace","C1":"Natural conversational pace","C2":"Natural conversational pace"}',
                                                          'BR-37: chỉ dẫn giọng đọc TTS theo cấp CEFR'),
 ('listening.max_speakers',   '2',                       'BR-37: số người nói tối đa trong một bài nghe (giới hạn multi-speaker của TTS)'),
 ('listening.max_chars',      '3000',                    'BR-37: số ký tự lời thoại tối đa một bài nghe'),
 ('star.writing_bands',       '[{"min_score":90,"stars":5},{"min_score":75,"stars":4},{"min_score":60,"stars":3},{"min_score":40,"stars":2},{"min_score":20,"stars":1}]',
                                                          'BR-18: quy đổi điểm Writing 0-100 sang Star'),
 ('ai.safety.block_threshold','"BLOCK_LOW_AND_ABOVE"',   'BR-15: ngưỡng lọc an toàn nội dung của Gemini')
ON CONFLICT (setting_key) DO NOTHING;

-- ---------------------------------------------------------------------
-- d. Luồng upload audio không còn dùng (giữ dòng, chỉ ghi chú)
-- ---------------------------------------------------------------------
UPDATE system_setting
SET description = 'Không dùng từ SRS 4.2 (Listening chuyển sang TTS, BR-37). Giữ dòng để không phá dữ liệu cũ.',
    updated_at  = now()
WHERE setting_key = 'import.max_audio_mb';
