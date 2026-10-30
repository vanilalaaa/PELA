-- V2: bỏ cột difficulty_level theo schema v1.4.
-- V1__init.sql (v1.3) đã merge nên không sửa; thay đổi đi qua migration mới.

-- Index lọc đề có chứa difficulty_level: Postgres tự xoá khi xoá cột, nên xoá rõ rồi tạo lại không có cột này.
DROP INDEX IF EXISTS idx_qb_filter;

ALTER TABLE vocabulary         DROP COLUMN difficulty_level;
ALTER TABLE listening_clip     DROP COLUMN difficulty_level;
ALTER TABLE question_bank      DROP COLUMN difficulty_level;
ALTER TABLE rag_question_cache DROP COLUMN difficulty_level;

CREATE INDEX idx_qb_filter ON question_bank(question_purpose, status, cefr_level, topic);
