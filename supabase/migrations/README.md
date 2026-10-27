# Migrations (Flyway)

Backend chạy Flyway với thư mục này (`spring.flyway.locations`, mặc định `filesystem:../supabase/migrations`).

- `V1__init.sql` = `schema.sql` v1.3.
  **TODO:** chưa có `schema.sql` v1.3 trong repo. Nhóm chép nguyên nội dung vào `V1__init.sql` rồi commit qua PR.
- Mỗi thay đổi schema là một file mới `V{n}__{mo_ta}.sql`. Không sửa migration đã merge.
- Tên bảng/cột `snake_case`, khoá chính UUID.
- Không chạy migration lên project Supabase đang dùng để test khi chưa thống nhất.
