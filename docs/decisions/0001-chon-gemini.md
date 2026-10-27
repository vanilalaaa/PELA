# 0001 — Chọn Google Gemini làm nhà cung cấp AI

- Trạng thái: Đã chấp nhận
- Liên quan: UC-24, MNT-3, MNT-4

## Bối cảnh

PELA cần LLM để chấm bài Writing, tạo đề Writing mới, xử lý file Admin nhập, và cần model embedding cho RAG / phát hiện đề trùng.

## Quyết định

- Sinh văn bản: `gemini-2.5-flash` (chấm Writing, tạo đề, xử lý file nhập).
- Embedding: `gemini-embedding-001`, 768 chiều, lưu bằng pgvector trên Supabase.
- Tên model, ngưỡng, giới hạn đọc từ bảng `system_setting`; prompt lưu ở `prompt_template` có version. Không hard-code.
- Mọi lời gọi đi qua port `application/port/out` của module tương ứng; adapter Gemini nằm ở `adapter/out`.

## Hệ quả

- Đổi model chỉ cần sửa cấu hình, không sửa code.
- Mỗi thành viên dùng Gemini API key riêng (AI Studio); key chỉ nằm ở backend.
- Đổi số chiều embedding đòi hỏi migration và tính lại toàn bộ vector.
