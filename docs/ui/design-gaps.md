# Thiếu sót thiết kế (design gaps)

Nhật ký các chỗ Figma chưa khớp hoặc chưa đủ so với SRS (`docs/srs/SRS_1.md`). Quy trình: xem `frontend/apps/mobile/AGENTS.md` và `frontend/apps/admin-web/AGENTS.md`, mục "Thiếu sót thiết kế".

- **Id**: `GAP-<số>`, tăng dần, không dùng lại.
- **Loại**: nhỏ (trạng thái lỗi/rỗng/khoá, thông báo, hộp xác nhận) hoặc lớn (màn mới, đổi luồng, thêm/bớt trường, hành vi khác SRS).
- **Trạng thái**: `cần duyệt` · `chờ quyết định` (thiếu lớn, đang hỏi) · `đã duyệt` · `đã sửa Figma` · `bỏ qua`.
- Figma trái SRS thì làm theo SRS và ghi lại ở đây. Không sửa SRS.

| Id | App | Màn | UC/BR | SRS yêu cầu | Figma đang có | Đề xuất | Loại | Trạng thái |
|---|---|---|---|---|---|---|---|---|
