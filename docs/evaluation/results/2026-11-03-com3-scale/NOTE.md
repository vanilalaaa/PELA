# COM-3 ở quy mô 10.000 đề, 03/11/2026

**Kết luận: đạt COM-3 trên dữ liệu tổng hợp, chạy local (Testcontainers), không gọi Gemini và không chạm Supabase.** Số đo là chỉ báo
về quy mô, không thay cho đo trên embedding thật.

## Cách đo

`RagIndexScaleTest` (`RAG_SCALE_TEST=true ./gradlew test --tests "com.pela.rag.evaluation.RagIndexScaleTest" -i`; mặc định bị bỏ qua
trong `./gradlew build` vì mất khoảng 1 phút rưỡi):

- Postgres 17 + pgvector (`pgvector/pgvector:pg17`), migration thật (V1 đến V11), index HNSW dựng lại đúng định nghĩa của V1 sau khi
  nạp hàng loạt.
- **10.000 đề Writing** (`status = active`) và **10.000 entry `rag_question_cache`**, rải đều 24 cặp topic + level (khoảng 417 đề mỗi cặp).
- Vector **tổng hợp**: 768 chiều, chuẩn hoá L2, cụm quanh một tâm riêng cho từng cặp topic + level, nhiễu Gauss (cosine đề với tâm
  khoảng 0.75). Không phải embedding thật.

## Kết quả (`com3-scale.csv`)

| Chỉ tiêu | Kết quả | Mục tiêu |
|---|---|---|
| Recall@5 của chính index HNSW (ép dùng index, tìm không lọc, 100 truy vấn, so với tìm chính xác) | **0.990** | ≥ 0.90 (COM-3) |
| Recall@5 truy hồi có lọc topic + level (RAG.Retrieve) | 1.000 | |
| Truy hồi RAG.Retrieve P50 / P95 | 68 ms / 82 ms | trong ngân sách PER-3 (kiểm: ≤ 1000 ms) |
| Tìm không lọc (`similarAtLeast`, phát hiện đề trùng) P95 | 78 ms | |
| Cache hit (tra cache + ghi lần phát) P50 / P95, mọi truy vấn trúng | 62 ms / 91 ms | ≤ 300 ms (PER-9) |
| Thời gian nạp dữ liệu và dựng hai index | 24,5 s | |

## Phát hiện về kế hoạch thực thi

- **Ở 10.000 đề, Postgres không dùng index HNSW cho các câu hỏi thật.** Truy hồi có lọc (topic + level, khoảng 400 đề mỗi cặp) dùng
  B-tree rồi sắp xếp; tìm không lọc quét tuần tự. Cả hai cho kết quả chính xác (recall 1.0) và vẫn nhanh (P95 < 100 ms).
- Để đo chất lượng của chính index HNSW, test **ép** dùng index (tắt quét tuần tự, bitmap scan và Sort): recall 0.990.
- Hệ quả: với kho cỡ này, COM-3 được thoả nhờ cả quét chính xác lẫn index. HNSW chỉ thực sự được dùng khi kho lớn hơn nhiều (planner
  tự chuyển khi chi phí quét tuần tự vượt chi phí index).

## Giới hạn

Vector tổng hợp, một container Docker trên máy dev, không có tải đồng thời, không có độ trễ mạng. Chưa đo với embedding thật ở quy mô
này (cần 10.000 đề thật).
