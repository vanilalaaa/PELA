-- V11: UC-24 (RAG tạo đề Writing): prompt rag_question_generation v2, ngưỡng "quá giống đề kho" và nhật ký sinh đề.
-- Không sửa V3 (prompt v1 'TODO v1'). Số V11 vì V10 đang do nhánh Listening dùng; nếu V10 chưa có trên develop lúc merge
-- thì đổi tên file thành số kế tiếp sau số lớn nhất trên develop (không chạy migration này lên DB chung trước đó).

-- ---------------------------------------------------------------------
-- 1. Prompt rag_question_generation v2 (RAG.Generate.Prompt, MNT-4)
-- ---------------------------------------------------------------------
UPDATE prompt_template SET is_active = false WHERE name = 'rag_question_generation' AND version = 1;

INSERT INTO prompt_template (name, version, system_text, body, response_schema, temperature, is_active, note) VALUES (
'rag_question_generation', 2,
$system$You write exactly ONE new English Writing prompt for Vietnamese learners of English at CEFR level {{level}}. The topic is "{{topic}}".

You are given example prompts about the same topic and level that teachers wrote. Write a NEW prompt that:
- stays on the topic "{{topic}}" and suits CEFR level {{level}}: the wording of the prompt itself must be easy enough for a {{level}} learner, and the task must not need knowledge or vocabulary beyond that level;
- keeps the task type and a word range that lies within the ranges of the examples (the learner must be able to answer within 20 to 300 words);
- is clearly different from every example: do not copy an example and do not only rephrase it; choose a different situation, angle or question;
- is one to three sentences, written as an instruction or question to the learner;
- contains no personal data, no brand names and nothing offensive, violent or adult.

The learner's interests, when given, may inspire the situation, but they never override the topic or the level.

task_type is one of: email, paragraph, essay. min_words and max_words are integers with 20 <= min_words < max_words <= 300.

Answer only with JSON that matches the schema.$system$,
$body$Topic: {{topic}}
Learner level: {{level}}
Learner interests (optional inspiration): {{interests}}
Task type rule: {{task_type_rule}}

Example prompts (task type and word range in brackets):
{{examples}}$body$,
$json$
{
  "type": "object",
  "properties": {
    "prompt_text": {"type": "string"},
    "task_type": {"type": "string", "enum": ["email", "paragraph", "essay"]},
    "min_words": {"type": "integer", "minimum": 20, "maximum": 300},
    "max_words": {"type": "integer", "minimum": 20, "maximum": 300}
  },
  "required": ["prompt_text", "task_type", "min_words", "max_words"]
}$json$::jsonb,
0.80, true,
'v2 (UC-24): một đề mới cùng topic, level, dạng bài; khoảng từ nằm trong khoảng của đề mẫu; không chép đề mẫu'
);

-- ---------------------------------------------------------------------
-- 2. Ngưỡng loại đề sinh ra quá giống đề trong kho (cùng topic + level)
-- ---------------------------------------------------------------------
INSERT INTO system_setting (setting_key, value, description) VALUES
 ('rag.generation.max_bank_similarity', '0.95',
  'RAG.Generate.Validate: đề do LLM sinh ra giống đề kho cùng topic + level >= ngưỡng này thì coi là chép đề mẫu và fallback (BR-7)')
ON CONFLICT (setting_key) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Nhật ký mỗi lần sinh đề: thống kê tỉ lệ fallback, cache hit, độ trễ (PER-3, PER-4, PER-9)
--    Không lưu nội dung đề hay truy vấn (không chứa dữ liệu bài viết của learner).
-- ---------------------------------------------------------------------
CREATE TABLE rag_generation_log (
    log_id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    topic           varchar(100) NOT NULL,
    cefr_level      varchar(2)   NOT NULL CHECK (cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    outcome         varchar(10)  NOT NULL CHECK (outcome IN ('generated', 'cache_hit', 'fallback')),
    reason          varchar(20)  CHECK (reason IN ('BELOW_THRESHOLD', 'TIMEOUT', 'INVALID_JSON', 'SAFETY_BLOCKED',
                                                   'FILTERED', 'DAILY_LIMIT', 'NO_EMBEDDING', 'INVALID_OUTPUT',
                                                   'UPSTREAM_ERROR')),
    top1_similarity numeric(5, 4),
    latency_ms      int          NOT NULL CHECK (latency_ms >= 0),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_log_reason_only_on_fallback CHECK ((outcome = 'fallback') = (reason IS NOT NULL))
);
COMMENT ON COLUMN rag_generation_log.reason IS 'Chỉ có khi outcome = fallback (BR-7, RAG.Fallback.OnFailure)';
COMMENT ON COLUMN rag_generation_log.top1_similarity IS 'Similarity Top-1 của lần truy hồi; NULL khi chưa truy hồi (hết lượt, lỗi embedding, cache hit)';
CREATE INDEX idx_rag_generation_log_created ON rag_generation_log(created_at);
CREATE INDEX idx_rag_generation_log_user    ON rag_generation_log(user_id, created_at DESC);
