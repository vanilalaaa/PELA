# 0006 — Lưu phiên khôi phục mật khẩu trong bộ nhớ backend

- Trạng thái: Đã chấp nhận
- Liên quan: Auth.Recover, SEC-5, UC-01 2.0.A2

## Bối cảnh

Khi learner nhập đúng mã khôi phục 6 số, Supabase Auth trả về một phiên khôi phục (access token có quyền đổi mật khẩu). Không được đưa access token này cho client: client chỉ nhận một **reset token ngẫu nhiên dùng một lần** (256 bit, hạn `auth.password_reset_ttl_minutes`, mặc định 10 phút). Backend phải giữ phiên khôi phục phía server cho tới khi learner đặt mật khẩu mới.

## Quyết định

- Phiên khôi phục lưu **trong bộ nhớ** của backend (`InMemoryPasswordResetSessionAdapter`, sau port `PasswordResetSessionPort`).
- Khoá tra cứu là SHA-256 của reset token; token gốc không được lưu.
- Mỗi user tối đa một phiên: xác minh mã lần nữa thì phiên cũ bị huỷ và thu hồi ở Supabase.
- Token bị xoá ngay khi dùng. Job `PasswordResetCleanupJob` (lịch `pela.jobs.password-reset-cleanup-delay`) dọn phiên quá hạn và thu hồi phiên Supabase tương ứng.

## Giới hạn đã biết

- **Mất khi backend khởi động lại.** Reset token đang chờ sẽ không còn hiệu lực (401 `INVALID_RESET_TOKEN`); learner chỉ cần xin mã mới (`POST /v1/auth/password/forgot`).
- **Chỉ đúng khi chạy một instance backend.** Nếu chạy nhiều instance sau load balancer, request đặt mật khẩu có thể vào instance không giữ phiên. Đồ án hiện triển khai một instance.
- Phiên khôi phục bị dọn ở PELA khi hết TTL, nhưng phía Supabase nó chỉ thực sự hết hạn theo thời hạn JWT. Việc thu hồi lúc dọn là best-effort.

## Hướng nâng cấp

Chuyển sang bảng DB (ví dụ `password_reset_session(token_hash, user_id, recovery_access_token, expires_at, consumed_at)`, bật RLS và không có policy cho learner) bằng một adapter mới cài cùng `PasswordResetSessionPort`; use case không đổi. Khi đó nên mã hoá `recovery_access_token` khi lưu.
