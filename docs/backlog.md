# Backlog

Việc đã biết phải làm nhưng chưa tới lượt. Khi bắt đầu một mục, ghi mã UC/BR liên quan vào PR và xoá mục khỏi đây.

## Identity / Account

- **Account.Delete (UC-04) và Admin.Accounts.Delete (UC-16):** khi tạo yêu cầu xoá phải gọi logout scope global (BR-33).
  Đặt `status = 'pending_deletion'` và `deletion_requested_at`, rồi gọi `SupabaseAuthPort.logout(..., LogoutScope.GLOBAL)`.
  Bộ lọc trạng thái tài khoản đã chặn mọi request `/v1/**` của tài khoản `pending_deletion`, nhưng refresh token phía Supabase vẫn cần thu hồi.
