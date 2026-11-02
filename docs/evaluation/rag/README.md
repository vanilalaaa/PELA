# Đánh giá RAG đề Writing (AS-7, COM-3, PER-3, PER-9)

Bộ công cụ đo phần truy hồi và sinh đề Writing bằng RAG. Các bài đo chạy trên **Supabase dev** dưới dạng test
`@Tag("external")`, nên **không** chạy trong `./gradlew build`.

| # | Chỉ tiêu | SRS | Công cụ | Trạng thái |
| --- | --- | --- | --- | --- |
| 1 | Hiệu chỉnh ngưỡng truy hồi | AS-7, BR-8 | `RagThresholdCalibrationTest` + `queries.csv` | Chạy được |
| 2 | Recall@5 của HNSW ≥ 0.90 | COM-3 | `RagHnswRecallTest` | Chạy được |
| 3 | P95 sinh đề ≤ 4 s; P95 cache hit ≤ 300 ms (không tính embed) | PER-3, PER-9 | `RagLatencyTest` (chế độ `local` để kết luận) | Chạy được; lần đo từ máy dev chưa kết luận được |
| 4 | ≥ 90% đề sinh ra đúng chủ đề và đúng trình độ (mẫu 50 đề, 2 người chấm) | PER-3 | `RagGenerationSampleTest`, `RagRatingReportTest`, `generated.csv` | Sinh đề và script chấm đều chạy được |

Mục 3 và mục sinh đề của mục 4 chạy trên pipeline UC-24 thật (`GenerateWritingPromptService` nối với DB Supabase dev và
Gemini). Chúng chạy trong **một transaction ghi rồi rollback** (không để lại cache, lần phát hay nhật ký) và dùng learner
seed `ad000000-…-0001` của `R__dev_seed.sql` làm người nhận; chỉ giới hạn "đề AI mỗi ngày" được nâng lên để đo liên tục.
Mục 1 và 2 chỉ đọc. Chốt ngưỡng: xem `docs/backlog.md` (mục RAG).

## Nguồn đề Writing và seed dev

- Nguồn: `docs/content/writing/Writing-Prompts-<A1..C2>.txt` (138 đề), dòng dạng
  `N. [Topic - type] Nội dung đề. (~min-max words)`. Bài nghe: `docs/content/listening/PELA-Listening-Import.csv`.
- Sinh lại seed khi file `.txt` đổi (chạy từ thư mục gốc repo, chỉ cần Python 3):

  ```bash
  python3 tools/content/gen_writing_seed.py
  ```

  Lệnh kiểm tra từng dòng (topic thuộc danh sách, type là email/paragraph/essay, `20 <= min < max <= 300`, không trùng
  chữ đề, số đề khớp dòng đầu file) và báo lỗi theo file + số dòng. Có lỗi thì không ghi file. Đầu ra là
  `db/seed/R__dev_seed_writing_prompts.sql`, không sửa tay.
- **Quy ước id:** `9b000000-0000-4000-8000-0LLNNN000000`, `LL` = 01..06 (A1..C2), `NNN` = số thứ tự trong file. Dải
  này không trùng `9a000000-…` (đề seed cũ) và `9b000000-…0101…` (câu hỏi nghe) của `R__dev_seed.sql`.
- **Chạy lại seed:** đề không đổi thì không bị đụng tới. Đề đổi `question_text` hoặc `payload` thì bị xoá embedding,
  về `retired`, để backfill embed lại. Đổi topic, level hay type của một đề đã seed **không** được áp dụng: dùng số
  thứ tự mới (id mới) hoặc xoá đề cũ.
- Đề mới nạp ở `retired` chưa có vector; chạy backfill (mục Chuẩn bị) để chuyển sang `active`.
- Test: `DevWritingPromptSeedIT` (138 đề, chạy lại không trùng, đề đổi bị xoá vector).

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
./gradlew externalTest --tests "com.pela.rag.evaluation.RagLatencyTest"
./gradlew externalTest --tests "com.pela.rag.evaluation.RagGenerationSampleTest"
./gradlew externalTest --tests "com.pela.rag.evaluation.RagRatingReportTest"
```

`RagLatencyTest` và `RagGenerationSampleTest` gọi Gemini thật (tốn quota): `RagLatencyTest` mặc định 20 truy vấn
on_topic (đổi bằng `RAG_LATENCY_SAMPLES`), `RagGenerationSampleTest` sinh 50 đề. Cần `GEMINI_API_KEY` và learner seed
(chạy profile dev ít nhất một lần).

Kết quả nằm trong `backend/build/evaluation/rag/`. Phần tóm tắt in ra stdout; xem bằng `--info` hoặc trong
`build/reports/tests/externalTest`.

## Đọc kết quả

### 1. Ngưỡng truy hồi (AS-7)

- `queries.csv` (cột `topic,level,interests,filter_topic,expected,note`) có 168 dòng, phủ đủ 24 cặp topic + level của bộ
  138 đề, mỗi cặp 7 dòng. **Truy vấn không phải câu tự do**: bài đo dựng truy vấn bằng
  `RagQueryBuilder.build(topic, level, interests)`, đúng hàm UC-24 dùng (SRS `RAG.Embed.Input`), còn truy hồi lọc theo
  `filter_topic` + level (cột trống = `topic`). Cột `interests` là các sở thích của `learner_profile.interests` (family,
  school, travel, food, sport, music, movie, tech, animal, health) ngăn bằng `|`; rỗng = không có sở thích.
  - **`on_topic` (5 dòng/cặp)**: yêu cầu bình thường, hệ thống nên tạo đề bằng AI: không có sở thích, hoặc sở thích liên quan
    topic (3 dòng), hoặc sở thích xa topic (2 dòng, `note = interest_far`). Sở thích chỉ để cá nhân hoá đề; learner đã tự
    chọn topic, nên truy hồi luôn lọc đúng topic + level và người thích động vật chọn topic Work vẫn là yêu cầu bình thường.
    Dòng `interest_far` được thống kê riêng để xem ảnh hưởng của sở thích.
  - **`off_topic` (2 dòng/cặp)**: truy vấn của topic A nhưng kho lọc theo topic B cùng level (`filter_topic` ≠ `topic`),
    mô phỏng **kho không có đề sát yêu cầu**. Đây là trường hợp ngưỡng phải chặn để chuyển sang đề có sẵn (BR-7).
  - Nhãn, sở thích và `filter_topic` do người duyệt, không để AI gắn.
- **Cột `note`:** `kho_gap` đánh dấu truy vấn đúng nhãn nhưng **kho Writing chưa có đề gần chủ đề đó**, nên similarity
  thấp và fallback BR-7 là hành vi đúng, không phải lỗi ngưỡng. Dòng `kho_gap` vẫn được đo và ghi vào `threshold.csv`,
  nhưng **loại khỏi phần tính ngưỡng đề xuất** (in riêng một dòng). Hiện chưa có dòng nào đánh dấu. Ví dụ từ bộ truy vấn
  câu tự do cũ (03/11/2026): "universal basic income" ở Society/C2 (0.582), vì chủ đề đó chỉ có trong bài nghe
  `C2-SOC-03`, không có đề Writing nào.
- **Kết quả đo bằng câu tự do (mốc 03/11/2026)** nằm ở `docs/evaluation/results/2026-11-03-content/`, chỉ để tham khảo,
  không dùng để chốt ngưỡng vì truy vấn khác hẳn truy vấn thật của UC-24.
- `threshold.csv` (cột `topic,level,interests,expected,query,top1_question_id,top1_similarity,note`): similarity Top-1 của từng truy vấn, sau khi lọc topic + level như RAG.Retrieve.
- Phần in ra gồm phân bố (min/p25/median/p75/max) của từng nhóm, giá trị `rag.retrieval.min_similarity` hiện tại và
  **ngưỡng đề xuất**:
  - phân bố riêng cho on_topic thường, `interest_far` và off_topic;
  - bảng ở các mức 0.60, 0.65, 0.68, 0.70: tỉ lệ on_topic được tạo đề, tỉ lệ `interest_far` được tạo đề, tỉ lệ off_topic bị
    chặn;
  - **ngưỡng đề xuất ưu tiên không chặn nhầm người học**: giá trị lớn nhất mà ≥ 99% dòng on_topic (gồm `interest_far`) vẫn
    được tạo đề, kèm tỉ lệ off_topic bị chặn ở ngưỡng đó. Bị chặn nhầm nghĩa là learner nhận đề có sẵn thay vì đề AI
    (không mất gì nhiều), còn đề AI lệch chủ đề thì không có bước nào chặn lại, nên ưu tiên chặn được off_topic nhưng không
    đánh đổi bằng việc chặn yêu cầu bình thường.
- **Travel/A2 và Work/B1 có ~11 đề** (5 của bộ 138 đề + 6 đề seed cũ `9a000000-…` của `R__dev_seed.sql`), nhiều hơn
  các cặp khác (5–13 đề), nên kết quả AS-7 ở hai cặp này lệch nhẹ. Đây là dữ liệu dev, không phải kho đề thật.

### 2. Recall@5 của HNSW (COM-3)

- So Top-5 dùng index (truy vấn thật của RAG.Retrieve: HNSW + iterative scan) với Top-5 tìm chính xác (cùng điều
  kiện, `SET LOCAL enable_indexscan = off`). Đo cả khi lọc topic + level và khi không lọc.
- `hnsw-recall.csv` ghi recall và hai danh sách id của từng truy vấn. Test **fail** nếu recall trung bình < 0.90.
- Phần in ra có `HNSW index used`. Với ít đề, planner thường quét tuần tự nên recall luôn bằng 1, tức là chưa đo được
  gì. COM-3 nhắm tới 10.000 đề, nên cần chạy lại khi kho đề đủ lớn.

### 3. Độ trễ (PER-3, PER-9)

`RagLatencyTest` đo trên một mẫu truy vấn on_topic rải đều các cặp topic + level (mặc định 20, đổi bằng `RAG_LATENCY_SAMPLES`),
Gemini luôn gọi thật. Hai chế độ (`RAG_LATENCY_MODE`):

- `remote` (mặc định): DB là Supabase dev, đo từ máy dev. Độ trễ mạng tới DB và tới Gemini chiếm phần lớn, nên **chỉ để tham
  khảo, không kiểm ngưỡng PER-3/PER-9**. Lần đo 03/11/2026 ở chế độ này: `docs/evaluation/results/2026-11-03-latency-dev/`.
- `local`: Postgres local (Testcontainers `pgvector/pgvector:pg17`, cần Docker): chạy migration và seed dev, rồi nạp embedding của
  150 đề từ fixture `docs/evaluation/rag/fixtures/question-bank-embeddings.csv`. Gemini vẫn gọi thật, nên số đo gần môi trường
  triển khai hơn (DB gần server, server gọi ra Google). **Kiểm** P95 ≤ 4 s và ≤ 300 ms. Dùng số này để kết luận.

Nội dung đo:

- `generateP95IsWithin4Seconds`: thời gian end-to-end của UC-24 (embed, cache, truy hồi, LLM, kiểm tra, lưu cache), gồm cả chọn
  đề dự phòng BR-7. Ghi `latency-generate.csv` (`topic,level,interests,branch,total_ms,db_ms,embed_ms,llm_ms,filter_ms,
  cache_write_ms,other_ms`) và in p50/p95 từng bước, độ trễ một vòng đi về tới DB (`SELECT 1`) và số lượt theo nhánh
  (generated, cache_hit hoặc fallback:<lý do>). Nếu LLM chiếm gần hết 4 s thì xem xét model nhẹ hơn cho riêng việc tạo đề.
- `cacheHitP95IsWithin300MillisExcludingEmbedding`: dựng trước một entry cache cho mỗi truy vấn mẫu, đo tra cache + ghi lần phát,
  **không tính** bước embed truy vấn; mọi truy vấn phải trúng entry của chính nó. Ghi `latency-cache-hit.csv`.
- Biến tuỳ chọn: `RAG_LATENCY_PAUSE_MS` (nghỉ giữa các lượt sinh đề để tránh hạn mức Gemini; không tính vào thời gian đo).
- Hai thiết lập giữ UC-24 trong ngân sách và tiết kiệm hạn mức: `rag.generation.min_llm_ms` (V11, mặc định 2000: còn ít hơn thì phát
  đề có sẵn ngay với lý do `BUDGET_EXHAUSTED`, không gọi Gemini) và `prompt_template.thinking_level` (V11: `rag_question_generation`
  v2 dùng `LOW`, mức thấp nhất của `gemini-3.8-flash`).

**Xuất fixture cho chế độ local** (chỉ đọc, không gọi Gemini; chạy một lần, hỏi trước khi chạy vì chạm Supabase dev):

```bash
./gradlew externalTest --tests "com.pela.rag.evaluation.RagFixtureExportTest"
```

Kết quả: `docs/evaluation/rag/fixtures/question-bank-embeddings.csv` (cột `question_id,embedding_model,embedding`). Chạy đo local:

```bash
RAG_LATENCY_MODE=local ./gradlew externalTest --tests "com.pela.rag.evaluation.RagLatencyTest" --rerun-tasks -i
```

Bảng `rag_generation_log` (mỗi lần UC-24 chạy ghi outcome, lý do fallback, top-1 similarity, latency) dùng để thống kê tỉ lệ
fallback và cache hit trên dữ liệu thật; các bài đo ở đây rollback nên không ghi vào bảng này. **Lưu ý khi đọc số liệu:**
`latency_ms` trong bảng đó được ghi ở cuối chuỗi `rag`, nên **chưa gồm bước chọn đề có sẵn (BR-7)** khi fallback; số đo đầy
đủ end-to-end là của `RagLatencyTest`.

### 4. Chất lượng đề sinh ra (PER-3): CẦN NGƯỜI CHẤM

1. `RagGenerationSampleTest` (**đo chất lượng đề, không đo thời gian**: hạn chót mỗi lượt là `RAG_SAMPLE_DEADLINE_MS`, mặc định
   60000 ms, và nghỉ giữa các lượt bằng `RAG_SAMPLE_PAUSE_MS`, mặc định 0, để không vượt hạn mức Gemini) sinh 50 đề do AI mới tạo (xen kẽ các cặp topic + level và các sở thích) vào `generated.csv` với cột
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

Kết quả sinh đề nằm ở `backend/build/evaluation/rag/generated.csv`; chép sang `docs/evaluation/rag/generated.csv` (hoặc đặt
`RAG_RATED_CSV`) để hai người chấm. `generated.csv` trong repo chỉ có dòng tiêu đề cho tới khi có bản đã chấm.
