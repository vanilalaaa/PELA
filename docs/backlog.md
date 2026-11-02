# Backlog

Việc đã biết phải làm nhưng chưa tới lượt. Khi bắt đầu một mục, ghi mã UC/BR liên quan vào PR và xoá mục khỏi đây.

## Identity / Account

- **Account.Delete (UC-04) và Admin.Accounts.Delete (UC-16):** khi tạo yêu cầu xoá phải gọi logout scope global (BR-33).
  Đặt `status = 'pending_deletion'` và `deletion_requested_at`, rồi gọi `SupabaseAuthPort.logout(..., LogoutScope.GLOBAL)`.
  Bộ lọc trạng thái tài khoản đã chặn mọi request `/v1/**` của tài khoản `pending_deletion`, nhưng refresh token phía Supabase vẫn cần thu hồi.

## Writing

- **Gamification nhận sự kiện chấm Writing:** cài adapter thật cho `WritingGradedEventPort` (hiện là
  `NoOpWritingGradedEventAdapter`), gọi ngay sau khi lưu kết quả chấm:
  - `star_event` với unique theo (source `writing`, `submission_id`), để gửi lại không cộng trùng (BR-36);
  - streak (BR-11);
  - bộ đếm sao mở khoá Final Test (BR-20);
  - thông báo `writing_graded`.
  Kèm job tạo bù cho các bài `graded` trước khi có adapter (đọc `writing_submission` có `graded_at`, `stars_earned`).

## RAG

- **Admin.QuestionBank.Retire phải hết hạn cache (BR-25):** khi Admin retire một đề Writing, gọi
  `ExpireRagCacheUseCase.expireBySourceTemplate(questionId)` (module rag, `port/in`) để các entry `rag_question_cache` có
  `source_template_id` đó chuyển sang `expired`. Hàm đã có và có test; module admin chưa gọi vì chưa có.
- **Chạy đo UC-24 trên Supabase dev và chấm đề (PER-3, PER-9):** bài đo đã viết (`RagLatencyTest`,
  `RagGenerationSampleTest`), cần chạy `RagLatencyTest` và `RagGenerationSampleTest` bằng `externalTest` (tốn quota Gemini),
  cho 2 người chấm `generated.csv`, rồi chạy `RagRatingReportTest`. Xem `docs/evaluation/rag/README.md`.
- **Đo lại ngưỡng truy hồi (AS-7) và chốt `rag.retrieval.min_similarity`:** UC-24 đã có hàm dựng truy vấn duy nhất
  (`RagQueryBuilder`) và `queries.csv` đã đổi sang cột `topic,level,interests,expected,note`. Cần chạy
  `RagThresholdCalibrationTest` trên Supabase dev, cả nhóm xem kết quả rồi chốt một con số; nếu đổi thì tạo migration
  mới, description ghi ngày đo và đường dẫn file kết quả. Mốc hiện tại: giữ 0.65. Kết quả đo bằng câu tự do 03/11/2026
  chỉ để tham khảo (`docs/evaluation/results/2026-11-03-content/`).
- **Ngưỡng truy hồi theo level (AS-7):** một ngưỡng chung `rag.retrieval.min_similarity = 0.65` chỉ chặn khoảng 10% truy vấn
  mà kho không có đề sát yêu cầu, vì similarity giảm dần theo trình độ (on_topic trung bình A1 0.787 … C2 0.702, cao hơn hoặc
  ngang off_topic của level thấp hơn). Ước lượng: ngưỡng riêng cho từng level chặn khoảng 60% (A1 ≈ 0.76, A2 ≈ 0.72, B1 ≈ 0.71,
  B2 ≈ 0.68, C1 ≈ 0.65, C2 ≈ 0.66); các số này chỉ là ước lượng trên chính dữ liệu dùng để chọn ngưỡng (xem
  `docs/evaluation/results/2026-11-03-query-builder/NOTE.md`). Việc cần làm: đổi setting thành bản đồ level → ngưỡng (kèm
  migration), sửa `GenerateWritingPromptService` đọc ngưỡng theo level của learner, rồi **đo lại với bộ truy vấn thứ hai**
  (không dùng lại bộ đã dùng để chọn ngưỡng) và chọn ngưỡng có biên an toàn để không chặn nhầm yêu cầu bình thường. Chỉ làm
  sau khi xong các UC chính.
