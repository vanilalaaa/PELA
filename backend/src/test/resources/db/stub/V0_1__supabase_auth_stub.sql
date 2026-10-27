-- CHỈ DÙNG KHI TEST (Testcontainers). Không đưa vào db/migrations.
-- Image pgvector/pgvector:pg17 không có những thứ Supabase tạo sẵn mà schema.sql tham chiếu:
-- schema auth, bảng auth.users, hàm auth.uid(), role authenticated. File này giả lập tối thiểu
-- để Flyway chạy được V1__init.sql. Chạy trước V1 nhờ version 0.1.

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id    uuid PRIMARY KEY,
    email text
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
END
$$;
