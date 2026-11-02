# Backlog

Việc đã biết phải làm nhưng chưa tới lượt. Khi bắt đầu một mục, ghi mã UC/BR liên quan vào PR và xoá mục khỏi đây.

## Identity / Account

- **Account.Delete (UC-04) và Admin.Accounts.Delete (UC-16):** khi tạo yêu cầu xoá phải gọi logout scope global (BR-33).
  Đặt `status = 'pending_deletion'` và `deletion_requested_at`, rồi gọi `SupabaseAuthPort.logout(..., LogoutScope.GLOBAL)`.
  Bộ lọc trạng thái tài khoản đã chặn mọi request `/v1/**` của tài khoản `pending_deletion`, nhưng refresh token phía Supabase vẫn cần thu hồi.

## Writing

- **P7/UC-24 bỏ chặn ai_generated:** `POST /v1/practice/writing/submissions` đang trả 422 `PROMPT_SOURCE_NOT_SUPPORTED`
  cho `promptSource = ai_generated` (`WritingSubmissionService.resolvePrompt`). Khi có UC-24 (RAG tạo đề), nhận đề từ
  `rag_question_cache`, lưu `rag_cache_id` + snapshot `prompt_text`, cập nhật OpenAPI.
- **Gamification nhận sự kiện chấm Writing:** cài adapter thật cho `WritingGradedEventPort` (hiện là
  `NoOpWritingGradedEventAdapter`), gọi ngay sau khi lưu kết quả chấm:
  - `star_event` với unique theo (source `writing`, `submission_id`), để gửi lại không cộng trùng (BR-36);
  - streak (BR-11);
  - bộ đếm sao mở khoá Final Test (BR-20);
  - thông báo `writing_graded`.
  Kèm job tạo bù cho các bài `graded` trước khi có adapter (đọc `writing_submission` có `graded_at`, `stars_earned`).
