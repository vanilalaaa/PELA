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

## RAG

- **P7 bỏ @Disabled RAG evaluation 3–4 và chạy đo:** khi có UC-24 (RAG.Generate, RAG.Cache, fallback BR-7), hiện thực
  `RagLatencyTest` (P95 generate ≤ 4 s — PER-3; P95 cache hit ≤ 300 ms không tính embed — PER-9) và
  `RagGenerationSampleTest` (sinh 50 đề ra `generated.csv`), bỏ `@Disabled("Chờ UC-24 (P7)")`, chạy trên Supabase dev,
  rồi cho 2 người chấm và chạy `RagRatingReportTest`. Xem `docs/evaluation/rag/README.md`.
- **P7 hàm dựng truy vấn duy nhất và đo lại ngưỡng (AS-7):** SRS `RAG.Embed.Input` dựng truy vấn từ topic + level +
  sở thích của người học. UC-24 phải có **một** hàm dựng truy vấn duy nhất; `RagThresholdCalibrationTest` phải gọi đúng
  hàm đó; `docs/evaluation/rag/queries.csv` đổi sang cột `topic,level,interests,expected,note` (`off_topic` = sở thích
  xa topic, ví dụ Work/A1 với sở thích "cooking, pets"). Chốt `rag.retrieval.min_similarity` sau khi đo lại theo định
  dạng này; nếu đổi thì tạo migration mới trong nhánh P7, description ghi ngày đo và đường dẫn file kết quả. Mốc hiện
  tại: giữ 0.65. Kết quả câu tự do 03/11/2026 chỉ để tham khảo (`docs/evaluation/results/2026-11-03-content/`).
