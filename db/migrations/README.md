# Migrations (Flyway)

Backend chạy Flyway với thư mục này (`spring.flyway.locations`, mặc định `filesystem:../db/migrations`).

- `V1__init.sql` = `schema.sql` v1.3.
  **TODO:** chưa có `schema.sql` v1.3 trong repo. Nhóm chép nguyên nội dung vào `V1__init.sql` rồi commit qua PR.
- Mỗi thay đổi schema là một file mới `V{n}__{mo_ta}.sql`. Không sửa migration đã merge.
- Tên bảng/cột `snake_case`, khoá chính UUID.
- Không chạy migration lên project Supabase đang dùng để test khi chưa thống nhất.
- Migration chỉ chạy bằng Flyway. Không dùng `supabase db push` / `supabase migration` (Supabase CLI có quy ước tên file riêng).
- Khi test, Flyway chạy thêm `backend/src/test/resources/db/stub/V0_1__supabase_auth_stub.sql` trước V1 để giả lập schema `auth` của Supabase.
