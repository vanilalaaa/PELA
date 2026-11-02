# Đánh giá chấm Writing bằng AI (PER-2)

PER-2 (SRS): chấm Writing bằng AI phải phát hiện lỗi ngữ pháp với Precision/Recall ≥ 85% trên bộ kiểm thử 100–150 câu có
đáp án do người chú thích, và ≥ 98% phản hồi là JSON hợp lệ, đúng định dạng. Precision/Recall báo cáo theo từng loại lỗi:
grammar, spelling, word_choice, repetition, punctuation.

## Bộ dữ liệu — `dataset.csv`

Hiện có 10 câu mẫu để kiểm tra công cụ. Cần bổ sung lên 100–150 câu trước khi kết luận PER-2.

| Cột | Ý nghĩa |
| --- | --- |
| `sentence` | Câu/đoạn ngắn learner viết (đặt trong `"..."` nếu có dấu phẩy). |
| `expected_issues` | Các lỗi THẬT, cách nhau bởi `\|`, mỗi lỗi dạng `category:đoạn_sai`. Để trống nếu câu không có lỗi. |

- `category` ∈ `grammar`, `spelling`, `word_choice`, `repetition`, `punctuation`.
- `style` là gợi ý, không phải lỗi, nên không chú thích và không tính vào điểm.
- `đoạn_sai` là đoạn trích nguyên văn trong câu (không phân biệt hoa thường), không chứa `|`. Ô có dấu phẩy thì đặt cả ô
  trong `"..."`, vd `"grammar:buy|punctuation:, it"`.
- Ví dụ: `"My freind and I usually goes to the library.",spelling:freind|grammar:goes`
- `WritingEvaluationDatasetTest` (chạy trong `./gradlew build`) kiểm tra định dạng file và việc mỗi `đoạn_sai` có trong câu.

## Cách chấm điểm

- Mỗi câu được gửi tới prompt `writing_grading` v2, đọc thẳng từ `db/migrations/V7__writing_grading_prompt_v2.sql`
  (system, body, response schema, temperature 0.2). Biến prompt được dựng giống `WritingGradingService`; đề là
  "free writing", trình độ lấy từ `WRITING_EVAL_LEVEL` (mặc định B1).
- Issue AI trả về được định vị bằng chính `WritingGrading.locate` của server. Issue không định vị được thì bị bỏ, giống
  khi chạy thật. Issue `style` bị loại.
- Một issue dự đoán là **đúng** (TP) nếu cùng loại và có vị trí chồng lên một lần xuất hiện của `đoạn_sai` kỳ vọng.
  - Precision = issue dự đoán đúng / tổng issue dự đoán.
  - Recall = issue kỳ vọng được tìm thấy / tổng issue kỳ vọng.
- JSON hợp lệ = số câu nhận được output qua kiểm tra schema / tổng số câu. Output không hợp lệ là lỗi `INVALID_JSON`.
  Lỗi mạng hoặc quá tải (`TIMEOUT`, `UPSTREAM_ERROR`, `RATE_LIMITED`) được thống kê riêng và không tính vào mẫu số.
- Mỗi câu chỉ gọi một lần, không thử lại, để đo đúng tỉ lệ JSON hợp lệ của một lần gọi.

## Chạy

Cần `GEMINI_API_KEY`, lấy từ biến môi trường hoặc `backend/.env`; key không bao giờ được in ra. Test này **không**
chạy trong `./gradlew build`.

```bash
cd backend
./gradlew externalTest --tests "com.pela.writing.WritingGradingEvaluationTest"
```

Windows: `.\gradlew.bat externalTest --tests "com.pela.writing.WritingGradingEvaluationTest"`

Biến tuỳ chọn:

- `WRITING_EVAL_DATASET`: đường dẫn CSV khác. Mặc định là `../docs/evaluation/writing-grading/dataset.csv`.
- `WRITING_EVAL_LEVEL`: trình độ learner.
- `GEMINI_TEXT_MODEL`: model. Mặc định `gemini-3.8-flash`, giống seed `ai.model.text`.

Kết quả:

- `backend/build/evaluation/writing-grading-results.csv`: Precision/Recall theo loại, cộng các dòng `overall` và
  `valid_json_rate`.
- `backend/build/evaluation/writing-grading-details.csv`: từng câu, gồm trạng thái, lỗi kỳ vọng và lỗi dự đoán.

Khi bộ dữ liệu có từ 100 câu trở lên, test sẽ **fail** nếu grammar Precision/Recall < 0.85 hoặc tỉ lệ JSON hợp lệ < 0.98.
Với ít hơn 100 câu, test chỉ ghi báo cáo.
