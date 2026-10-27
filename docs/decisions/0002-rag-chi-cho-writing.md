# 0002 — RAG chỉ dùng cho Writing

- Trạng thái: Đã chấp nhận
- Liên quan: UC-24, BR-7

## Bối cảnh

RAG tốn chi phí gọi AI và cần mạng. Phần lớn nội dung học (Lesson, Challenge) không cần sinh động.

## Quyết định

- RAG chỉ dùng cho (1) "Để AI tạo đề Writing mới" (UC-24) và (2) phát hiện đề trùng khi Admin nhập kho đề.
- Truy hồi: lọc bắt buộc theo `topic` + `level`, ngưỡng tương đồng 0.65.
- Cache: nếu đề mới tạo có độ tương đồng ≥ 0.92 với đề đã có thì dùng lại đề đã có.
- Lỗi AI hoặc không có kết quả trên ngưỡng → trả đề có sẵn trong kho (BR-7).
- Các ngưỡng là cấu hình trong `system_setting`.
- Daily/Weekly Challenge sinh theo luật, không gọi AI, chạy được offline.

## Hệ quả

- Người dùng luôn nhận được đề, kể cả khi Gemini lỗi.
- Không mở rộng RAG sang tính năng khác khi chưa có ADR mới.
