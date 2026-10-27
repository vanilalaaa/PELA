# 0003 — Offline-first với bảng outbox

- Trạng thái: Đã chấp nhận
- Liên quan: BR-23, BR-26, BR-36

## Bối cảnh

Người học cần học được khi mất mạng. Đồng thời Star, streak, SM-2, kết quả bài thi phải đáng tin cậy.

## Quyết định

- Mobile dùng WatermelonDB làm nguồn dữ liệu cục bộ. Mọi thao tác ghi khi offline được ghi vào bảng `outbox`.
- Client gửi đáp án thô theo lô; mỗi bản ghi có UUID do client sinh để gửi lại an toàn (idempotent, ràng buộc unique phía server).
- Server là trọng tài: Star, streak, SM-2, kết quả bài thi luôn được tính lại từ đáp án thô (BR-23). Client chỉ hiển thị kết quả tạm thời.
- Logic dùng chung (SM-2, quy đổi Star, sinh câu hỏi theo luật, đếm từ) viết ở `frontend/packages/domain`, kiểm chứng chéo với backend Java bằng test vector trong `frontend/packages/fixtures`.

## Hệ quả

- Kết quả hiển thị offline có thể được server điều chỉnh khi đồng bộ.
- Mỗi bảng dùng offline phải có trong schema WatermelonDB và có migration tương ứng.
