# Đo ngưỡng truy hồi RAG với truy vấn dựng bằng RagQueryBuilder, 03/11/2026

**Kết luận: giữ `rag.retrieval.min_similarity = 0.65`, không đổi ngưỡng.** Lý do ở cuối file.

## Cách đo

- Kho: 150 đề Writing đã có embedding (138 đề trong `docs/content/writing` + 12 đề seed `9a000000-…`), Supabase dev.
- Truy vấn: 168 dòng của `docs/evaluation/rag/queries.csv` (7 dòng × 24 cặp topic + level), mỗi truy vấn dựng bằng
  `RagQueryBuilder.build(topic, level, interests)`, đúng hàm UC-24 dùng (SRS `RAG.Embed.Input`). Test
  `RagThresholdCalibrationTest`, chỉ đọc.
- `on_topic` (120 dòng): yêu cầu bình thường, gồm 48 dòng `interest_far` (sở thích xa topic). `off_topic` (48 dòng): truy
  vấn của topic A nhưng kho lọc theo topic B cùng level (`filter_topic`), mô phỏng kho không có đề sát yêu cầu.
- `threshold.csv`: similarity Top-1 của từng truy vấn (cột `topic,level,interests,filter_topic,expected,query,
  top1_question_id,top1_similarity,note`).

## Phân bố similarity Top-1

| Nhóm | n | min | p25 | median | p75 | max |
|---|---|---|---|---|---|---|
| on_topic thường | 72 | 0.657 | 0.711 | 0.731 | 0.767 | 0.811 |
| on_topic `interest_far` | 48 | 0.654 | 0.696 | 0.710 | 0.740 | 0.782 |
| off_topic | 48 | 0.635 | 0.671 | 0.691 | 0.711 | 0.742 |

## Theo ngưỡng

| Ngưỡng | on_topic được tạo đề | `interest_far` được tạo đề | off_topic bị chặn |
|---|---|---|---|
| 0.600 | 100.0% | 100.0% | 0.0% |
| **0.650 (hiện tại)** | **100.0%** | **100.0%** | **10.4%** |
| 0.657 (test đề xuất, giữ ≥ 99% on_topic) | 99.2% | 97.9% | 12.5% |
| 0.680 | 90.8% | 83.3% | 31.3% |
| 0.700 | 77.5% | 66.7% | 64.6% |

42/48 dòng off_topic có similarity ≥ 0.657.

## Độ tách giữa on_topic và off_topic (tính từ `threshold.csv`)

- AUC on_topic so với off_topic: **0.80**; chỉ lấy on_topic thường (không tính `interest_far`): **0.85**.
- AUC on_topic thường so với `interest_far`: 0.64 (sở thích xa topic chỉ hạ similarity nhẹ).
- **18/24 cặp** topic + level có mọi dòng on_topic cao hơn mọi dòng off_topic.
- Similarity trung bình theo level (giảm dần từ A1 đến C2, kéo hai nhóm chồng lên nhau khi gộp mọi level):

| Level | on_topic thường | `interest_far` | off_topic |
|---|---|---|---|
| A1 | 0.787 | 0.770 | 0.717 |
| A2 | 0.766 | 0.757 | 0.714 |
| B1 | 0.738 | 0.717 | 0.680 |
| B2 | 0.723 | 0.704 | 0.684 |
| C1 | 0.709 | 0.686 | 0.681 |
| C2 | 0.702 | 0.686 | 0.662 |

Off_topic của A1 (0.717) cao hơn on_topic của C2 (0.702), nên một ngưỡng chung không tách được hai nhóm.

## Vì sao giữ 0.65

- Ngưỡng đề xuất 0.657 chỉ chênh 0.007 so với 0.65 (nhiễu), nhưng đã bắt đầu loại on_topic.
- Muốn chặn nhiều off_topic bằng một ngưỡng chung phải trả giá lớn: ở 0.68 chặn nhầm 9.2% yêu cầu bình thường (16.7% với
  `interest_far`) mà chỉ chặn thêm khoảng 21 điểm phần trăm off_topic. Chặn nhầm nghĩa là learner nhận đề có sẵn thay vì đề
  AI, và cá nhân hoá giảm mà không an toàn hơn.
- Truy hồi luôn lọc đúng topic + level nên đề mẫu vốn cùng chủ đề; đề AI còn qua kiểm tra dạng bài, khoảng từ, từ cấm và độ
  giống đề kho.
- Ngưỡng theo level tách tốt hơn rõ (xem bên dưới) nhưng là thay đổi thiết kế, ghi ở `docs/backlog.md`.

## Ước lượng ngưỡng theo level (chỉ để tham khảo, chưa dùng)

Đặt ngưỡng riêng cho mỗi level ở mức giữ 100% on_topic của chính level đó cho kết quả (A1 ≈ 0.76, A2 ≈ 0.72, B1 ≈ 0.71, B2 ≈
0.68, C1 ≈ 0.65, C2 ≈ 0.66): chặn khoảng 60% off_topic (29/48) thay vì 12.5%. **Các số này tính trên chính dữ liệu dùng để
chọn ngưỡng** (mỗi level chỉ 20 dòng on_topic và 8 dòng off_topic) nên chưa kiểm chứng; cần bộ truy vấn thứ hai và biên an
toàn trước khi áp dụng.
