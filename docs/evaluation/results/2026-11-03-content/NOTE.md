# Đo ngưỡng truy hồi RAG, 03/11/2026 (mốc tham khảo)

- Kho: 150 đề Writing đã có embedding (138 đề trong `docs/content/writing` + 12 đề seed `9a000000-…`), Supabase dev.
- Truy vấn: 133 câu **tự do** (`docs/evaluation/rag/queries.csv` lúc đó, chưa có cột `note`): 96 `on_topic`, 37
  `off_topic`, 24 cặp topic + level. Test `RagThresholdCalibrationTest`, chỉ đọc.
- `threshold.csv`: similarity Top-1 từng truy vấn (cột `topic,level,expected,query,top1_question_id,top1_similarity`).

| Nhóm | n | min | median | max |
|---|---|---|---|---|
| on_topic | 96 | 0.582 | 0.789 | 0.888 |
| off_topic | 37 | 0.489 | 0.586 | 0.697 |

Ngưỡng hiện tại `rag.retrieval.min_similarity = 0.65` (giữ nguyên). Test đề xuất 0.697 (độ chính xác 0.977,
`separable=false`), **không áp dụng**.

| Ngưỡng | on_topic bị loại | off_topic lọt | Độ chính xác |
|---|---|---|---|
| 0.65 | 1 | 6 | 0.947 |
| 0.68 | 1 | 3 | 0.970 |
| 0.70 | 5 | 0 | 0.962 |

## Lưu ý khi đọc

- on_topic thấp nhất (0.582) là `Society/C2 | Is a universal basic income better than targeted welfare?`: kho Writing
  chưa có đề nào về chủ đề này (`kho_gap`), fallback BR-7 là hành vi đúng. Bỏ dòng này thì ở 0.65 không còn on_topic nào
  bị loại.
- Các off_topic lọt cao nhất đều ở A1 (0.688–0.697) vì truy vấn cùng khuôn "Write about your…" / "Write an email to…" với
  đề A1 trong kho. Truy vấn thật (SRS `RAG.Embed.Input`) ghép topic + level + sở thích, không phải câu tự do, nên kết quả
  này **chỉ để tham khảo, không dùng để chốt ngưỡng**.
- Chốt ngưỡng sau khi UC-24 (P7) có hàm dựng truy vấn duy nhất và bài đo gọi lại đúng hàm đó (`docs/backlog.md`).

## Recall@5 của HNSW (COM-3), cùng ngày

- `hnsw-recall.csv`: 150 đề active, 133 truy vấn. Recall@5 lọc topic + level = 1.000; không lọc (toàn bộ đề active) = 1.000
  (mục tiêu ≥ 0.90).
- **Chưa chứng minh được COM-3:** Postgres không dùng index `idx_qb_embedding_hnsw` (`HNSW index used = false`) vì quá ít
  hàng, nên recall luôn đúng tuyệt đối. COM-3 nhắm tới 10.000 đề; cần đo lại trên kho đủ lớn (ví dụ test Testcontainers
  với 10.000 vector).
