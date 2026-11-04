# Hướng dẫn chấm đề do AI sinh (P8, việc 9 và việc 12)

Hai việc cần người thật chấm. Công cụ trong `tools/evaluation/` chỉ dùng thư viện chuẩn của Python 3, không gọi mạng, không chạm Supabase hay Gemini.

## Việc 9: chấm 50 đề do AI sinh (hai người độc lập)

Đầu vào là `generated.csv` do `RagGenerationSampleTest` ghi ra (cột `id, topic, level, prompt_text, ...`).

1. Tạo file chấm có đúng tám cột: `id, topic, level, prompt_text, on_topic_r1, right_level_r1, on_topic_r2, right_level_r2`. Bốn cột chấm để trống.
2. Mỗi người nhận **một bản sao riêng**, không xem bản của người kia.
   - Người 1 chỉ điền `on_topic_r1` và `right_level_r1`.
   - Người 2 chỉ điền `on_topic_r2` và `right_level_r2`.
3. Cách chấm, mỗi ô là `y` hoặc `n`:
   - `on_topic`: đề đúng chủ đề (topic) ghi trong dòng đó.
   - `right_level`: độ khó và từ vựng phù hợp trình độ CEFR ghi trong dòng đó (không quá dễ, không quá khó).
4. Ghép hai bản:

   ```bash
   python3 tools/evaluation/merge_ratings.py --rater1 rater1.csv --rater2 rater2.csv --out generated-rated.csv
   ```

   Script từ chối ghép và liệt kê theo `id` nếu: hai file không cùng bộ đề, một người điền cột của người kia (mất tính độc lập), hoặc có ô khác `y`/`n`.
5. Chạy `RagRatingReportTest` với biến `RAG_RATED_CSV` trỏ tới `generated-rated.csv` để lấy tỷ lệ đạt và độ đồng thuận của hai người.

## Việc 12: A/B mù, đề RAG so với đề trong kho

1. Dựng bảng chấm (N đề RAG + N đề kho cùng topic và level, trộn ngẫu nhiên, không lộ nguồn):

   ```bash
   python3 tools/evaluation/build_ab_sheet.py --rag generated.csv --bank docs/content/writing --out-dir ab --n 20 --seed 7
   ```

   Kết quả: `ab/ab-sheet.csv` gửi cho người chấm, `ab/ab-key.csv` **giữ kín** cho tới khi chấm xong.
2. Mỗi người chấm một bản sao riêng của `ab-sheet.csv`, điền hai cột bằng số nguyên 1 đến 5:
   - `appeal_1_5`: đề có hấp dẫn, gợi được ý để viết không.
   - `fit_1_5`: đề có phù hợp trình độ và chủ đề ghi trong dòng không.
3. Tổng hợp:

   ```bash
   python3 tools/evaluation/summarize_ab.py --key ab/ab-key.csv --sheet r1.csv --sheet r2.csv --out ab/report.md
   ```

   Báo cáo gồm điểm trung bình theo nguồn, chênh lệch RAG trừ kho và khoảng tin cậy 95% (bootstrap theo đề, có seed nên lặp lại được). Mẫu nhỏ nên chỉ nêu là kết quả tham khảo.

Bộ đề kho có 138 đề; `--n` không được vượt số đề kho còn lại của từng cặp topic và level, nếu không script báo lỗi và không ghi file.

## Chạy test của công cụ

```bash
python3 -I -m unittest discover -s tools/evaluation -v
```
