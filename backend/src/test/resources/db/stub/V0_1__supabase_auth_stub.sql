-- CHỈ DÙNG KHI TEST (Testcontainers). Không đưa vào db/migrations.
-- Image pgvector/pgvector:pg17 không có những thứ Supabase tạo sẵn mà schema.sql v1.3 tham chiếu:
--   - auth.users (trigger đọc id, email, raw_user_meta_data)
--   - auth.uid() (RLS, is_admin())
--   - role authenticated (GRANT/POLICY), role anon (REVOKE)
-- File này giả lập tối thiểu để Flyway chạy được V1__init.sql. Chạy trước V1 nhờ version 0.1.
-- Test lỗi vì thiếu đối tượng Supabase nào thì bổ sung vào đây; không sửa V1 cho vừa test.

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id                 uuid PRIMARY KEY,
    email              text,
    raw_user_meta_data jsonb DEFAULT '{}'::jsonb
);

-- Giống Supabase: lấy sub từ claim JWT của request hiện tại; test có thể đặt bằng
-- SET request.jwt.claim.sub = '<uuid>';
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE ROLE authenticated NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
END
$$;
