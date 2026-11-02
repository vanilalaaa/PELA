# Đo độ trễ UC-24 từ máy dev, 03/11/2026 (chưa dùng để kết luận PER-3 / PER-9)

**Đo từ máy dev qua mạng tới Supabase dev và Gemini, nên kết quả bị độ trễ mạng chi phối và không dùng để kết luận PER-3
(P95 ≤ 4 s) hay PER-9 (P95 cache hit ≤ 300 ms).** Số đo có giá trị chẩn đoán: nó chỉ ra các vấn đề được sửa ở các commit sau.

## Cách đo

`RagLatencyTest` (phiên bản đầu, chưa chia nhỏ theo bước), 20 truy vấn on_topic rải đều các cặp topic + level, pipeline UC-24 thật
nối với DB Supabase dev và Gemini thật, trong một transaction rollback. Prompt `rag_question_generation` v2, model
`gemini-3.8-flash`.

## Kết quả

| Đo | Kết quả | Mục tiêu |
|---|---|---|
| Sinh đề (PER-3) | n=20, p50 4816 ms, p95 4928 ms; **0/20 đề AI được tạo**: 13 `fallback:TIMEOUT`, 7 `fallback:UPSTREAM_ERROR` | P95 ≤ 4000 ms |
| Cache hit, không tính embed (PER-9) | n=20, p50 1043 ms, p95 1062 ms (khoảng 829 đến 1063 ms); 0 lượt trượt cache | P95 ≤ 300 ms |

File thô: `latency-generate.csv`, `latency-cache-hit.csv`.

## Nguyên nhân (từ log và hai file CSV)

1. **13 TIMEOUT do hết ngân sách trước khi tới LLM.** Log Gemini ghi `timed out after PT0.7…S`: lúc gọi LLM chỉ còn khoảng 0,7 đến
   0,86 s trong ngân sách 4 s (`ai.question_gen_p95_budget_ms`). Khoảng 3,2 s đã tiêu cho khoảng 8 câu SQL tuần tự tới Supabase và
   một lần embed truy vấn qua mạng. Các lượt gọi chắc chắn quá hạn này vẫn tốn hạn mức Gemini.
2. **7 lỗi 429 `RESOURCE_EXHAUSTED: You exceeded your current quota`** từ Gemini (một khối liên tiếp), tức là hạn mức của gói
   miễn phí cho model văn bản; chưa rõ theo phút hay theo ngày.
3. **Cache hit tập trung quanh 1040 ms** (20 lượt, từ 829 đến 1063 ms, p50 1045; 4 lượt đầu nhanh hơn, 829 đến 855 ms): đặc điểm
   của độ trễ mạng gần như cố định cho khoảng 4 đến 5 câu SQL, không phải thời gian xử lý.

## Đã sửa sau lần đo này

- `rag.generation.min_llm_ms` (V11, mặc định 2000): còn ít hơn thì phát đề có sẵn ngay (`BUDGET_EXHAUSTED`), không gọi Gemini.
- Mức thinking của Gemini cấu hình theo prompt (`prompt_template.thinking_level`, V12; `rag_question_generation` v2 dùng `LOW`,
  mức thấp nhất của `gemini-3.8-flash`).
- `RagLatencyTest` đo chi tiết từng bước (db, embed, llm, filter, cache_write) và độ trễ `SELECT 1`, có chế độ
  `RAG_LATENCY_MODE=local` (Postgres Testcontainers nạp embedding từ fixture, Gemini vẫn thật) để lấy số so sánh được.
- `RagGenerationSampleTest` dùng hạn chót 60 s và có thể nghỉ giữa các lượt.

Cần đo lại: chế độ local cho PER-3 và PER-9, và kiểm tra hạn mức Gemini trước khi chạy (https://ai.dev/rate-limit).
