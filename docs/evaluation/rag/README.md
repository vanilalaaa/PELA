# Đánh giá RAG đề Writing (AS-7, COM-3, PER-3, PER-9)

Bộ công cụ đo phần truy hồi và sinh đề Writing bằng RAG. Các bài đo chạy trên **Supabase dev** dưới dạng test
`@Tag("external")`, nên **không** chạy trong `./gradlew build`.

| # | Chỉ tiêu | SRS | Công cụ | Trạng thái |
| --- | --- | --- | --- | --- |
| 1 | Hiệu chỉnh ngưỡng truy hồi | AS-7, BR-8 | `RagThresholdCalibrationTest` + `queries.csv` | Chạy được |
| 2 | Recall@5 của HNSW ≥ 0.90 | COM-3 | `RagHnswRecallTest` | Chạy được |
| 3 | P95 sinh đề ≤ 4 s; P95 cache hit ≤ 300 ms (không tính embed) | PER-3, PER-9 | `RagLatencyTest` | `@Disabled("Chờ UC-24 (P7)")` |
| 4 | ≥ 90% đề sinh ra đúng chủ đề và đúng trình độ (mẫu 50 đề, 2 người chấm) | PER-3 | `RagGenerationSampleTest`, `RagRatingReportTest`, `generated.csv` | Sinh đề: chờ UC-24. Script chấm: chạy được |

Mục 3 và mục sinh đề của mục 4 cần pipeline UC-24 (RAG.Generate, RAG.Cache), hiện chưa có. Việc còn lại đã ghi ở
`docs/backlog.md` (mục RAG, P7).

## An toàn dữ liệu

- Các test không dựng Spring context nên Flyway không chạy, không đổi schema.
- Mọi câu truy vấn chạy trong transaction `READ ONLY` và luôn rollback. `SET LOCAL` (iterative scan, tắt index khi tìm
  chính xác) chỉ có hiệu lực trong transaction đó.
- Không ghi dữ liệu thật. Riêng đo nhánh cache hit (mục 3, khi có UC-24) có thể phải tạo bản cache, cũng trong
  transaction rollback.
- **Không tự sửa `system_setting`.** Mục 1 chỉ in ngưỡng đề xuất; sửa hay không là việc của nhóm.

## Chuẩn bị

1. Cấu hình: `DB_URL`, `DB_USER`, `DB_PASSWORD` (trỏ tới Supabase dev) và `GEMINI_API_KEY`, đặt trong biến môi trường
   hoặc `backend/.env` (xem `.env.example`). Test không in giá trị ra. Thiếu biến thì test **skip** và ghi lý do.
2. **Chạy backfill embedding trước** để mọi đề Writing có vector và đề seed được kích hoạt:
   `POST /v1/admin/question-bank/embeddings/backfill`, hoặc khởi động backend với profile dev. Nếu một cặp topic +
   level chưa có đề active, mục 1 sẽ dừng và báo.
3. Model embedding và số chiều được đọc từ `system_setting` của DB (`ai.model.embedding`, `ai.embedding.dimensions`),
   nên luôn khớp với vector đã lưu.

## Chạy

Trong `backend/` (Windows dùng `.\gradlew.bat`):

```bash
./gradlew externalTest --tests "com.pela.rag.evaluation.RagThresholdCalibrationTest"
./gradlew externalTest --tests "com.pela.rag.evaluation.RagHnswRecallTest"
./gradlew externalTest --tests "com.pela.rag.evaluation.RagRatingReportTest"
```

Kết quả nằm trong `backend/build/evaluation/rag/`. Phần tóm tắt in ra stdout; xem bằng `--info` hoặc trong
`build/reports/tests/externalTest`.

## Đọc kết quả

### 1. Ngưỡng truy hồi (AS-7)

- `queries.csv` có 45 truy vấn (cột `topic,level,query,expected`): 30 `on_topic` và 15 `off_topic`, lấy theo hai cặp
  có trong seed (Travel/A2, Work/B1). Truy vấn `off_topic` vẫn lọc theo topic + level nhưng nội dung lạc đề, mô phỏng
  việc truy hồi không tìm được đề phù hợp.
- `threshold.csv`: similarity Top-1 của từng truy vấn, sau khi lọc topic + level như RAG.Retrieve.
- Phần in ra gồm phân bố (min/p25/median/p75/max) của từng nhóm, giá trị `rag.retrieval.min_similarity` hiện tại và
  **ngưỡng đề xuất**:
  - hai nhóm tách hẳn (`separable=true`): lấy điểm giữa max(off_topic) và min(on_topic);
  - hai nhóm chồng nhau: chọn ngưỡng có độ chính xác cao nhất; nếu hoà thì chọn ngưỡng loại ít đề on_topic hơn (đề
    bị loại vẫn có BR-7 dự phòng). Phần in ra ghi rõ số đề bị phân loại sai.
- **Ngưỡng đo trên seed hiện tại (12 đề, 2 cặp topic/level) chỉ để tham khảo.** AS-7 yêu cầu hiệu chỉnh lại trên
  toàn bộ đề Writing trước khi test người dùng. Khi đó cần thêm truy vấn cho mọi cặp topic/level vào `queries.csv`.

### 2. Recall@5 của HNSW (COM-3)

- So Top-5 dùng index (truy vấn thật của RAG.Retrieve: HNSW + iterative scan) với Top-5 tìm chính xác (cùng điều
  kiện, `SET LOCAL enable_indexscan = off`). Đo cả khi lọc topic + level và khi không lọc.
- `hnsw-recall.csv` ghi recall và hai danh sách id của từng truy vấn. Test **fail** nếu recall trung bình < 0.90.
- Phần in ra có `HNSW index used`. Với ít đề, planner thường quét tuần tự nên recall luôn bằng 1, tức là chưa đo được
  gì. COM-3 nhắm tới 10.000 đề, nên cần chạy lại khi kho đề đủ lớn.

### 3. Độ trễ (PER-3, PER-9): chờ UC-24

Khi có UC-24, `RagLatencyTest` sẽ đo:

- P95 thời gian sinh đề end-to-end, gồm cả fallback BR-7: phải ≤ 4 s;
- P95 nhánh cache hit, không tính bước embed truy vấn: phải ≤ 300 ms.

Kết quả ghi ra `latency.csv`.

### 4. Chất lượng đề sinh ra (PER-3): CẦN NGƯỜI CHẤM

1. (Chờ UC-24) `RagGenerationSampleTest` sinh 50 đề vào `generated.csv` với cột
   `id,topic,level,prompt_text,on_topic_r1,right_level_r1,on_topic_r2,right_level_r2`; 4 cột cuối để trống.
2. **Hai người chấm độc lập** điền `y`/`n` vào cột của mình (`_r1` hoặc `_r2`), không xem cột của người kia:
   - `on_topic`: đề đúng chủ đề được yêu cầu;
   - `right_level`: độ khó và yêu cầu phù hợp trình độ CEFR của dòng đó.
3. Chạy `RagRatingReportTest`. File khác thì đặt `RAG_RATED_CSV=<đường dẫn>`. Dòng nào chưa điền đủ 4 ô thì test fail
   và liệt kê id.
4. Đọc kết quả:
   - **Tỉ lệ đạt**: một đề đạt khi cả `on_topic` và `right_level` đều là `y`. Có tỉ lệ đạt của từng người và của
     trường hợp cả hai cùng cho đạt; mục tiêu ≥ 0.90.
   - **Đồng thuận**: phần trăm trùng nhau và Cohen's kappa cho `on_topic`, `right_level` và kết quả đạt. Kappa thấp
     (dưới khoảng 0.6) nghĩa là hai người hiểu tiêu chí khác nhau, nên thống nhất lại tiêu chí rồi chấm lại trước
     khi kết luận.

Hiện `generated.csv` chỉ có dòng tiêu đề, nên `RagRatingReportTest` sẽ skip.
