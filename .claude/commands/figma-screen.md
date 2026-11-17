---
description: Dựng một màn từ Figma theo quy trình P3 (mobile hoặc admin-web)
argument-hint: <Tên màn> <UC-xx> <link Figma>
---

# P3 · Dựng một màn từ Figma

Đầu vào: `$ARGUMENTS`, gồm tên màn, mã UC và link node Figma, ví dụ `05 Login UC-01 https://www.figma.com/design/1lvmT8y6aAhsOYil7blaXE/Personalized-English-Learning?node-id=613-14609`. Thiếu một trong ba thì hỏi lại, không đoán.

Quy tắc gốc: `AGENTS.md`, `frontend/apps/mobile/AGENTS.md` (màn mobile) hoặc `frontend/apps/admin-web/AGENTS.md` (màn admin). Trả lời và viết tài liệu bằng tiếng Việt; code, tên file, key i18n, commit bằng tiếng Anh.

## 0. Chuẩn bị
- Đọc dòng của màn trong `docs/ui/figma-map.md`: frame chính, các frame biến thể, UC. Màn chưa có trong bản đồ thì thêm dòng trước khi làm.
- Đọc các GAP liên quan trong `docs/ui/design-gaps.md`.
- Kiểm tra nhánh hiện tại (`feature/uyen-…`); không làm trên `develop`/`main`.

## 1. Lấy thiết kế (tiết kiệm lượt gọi Figma)
- Nạp skill `figma:figma-design-to-code` trước khi gọi `get_design_context`.
- `create_session` với fileKey `1lvmT8y6aAhsOYil7blaXE` (một lần cho cả màn), rồi `get_design_context` và `get_screenshot` cho **frame chính**.
- Frame biến thể: chỉ gọi khi bố cục khác frame chính (ví dụ sheet mở, dialog). Không gọi lại node đã đọc trong phiên.
- Bị báo vượt giới hạn hoặc kết quả quá lớn: dừng và báo, không thử lại liên tục.
- Code Figma trả về (React + Tailwind) chỉ để tham khảo. Không dán nguyên.

## 2. Đối chiếu SRS
- Đọc UC trong `docs/srs/SRS_1.md` (SRS v4.3): luồng chính, luồng phụ, ngoại lệ, BR, FR liên quan.
- Lập checklist trạng thái: loading, empty, error, offline, cùng mọi biến thể Figma và mọi luồng phụ/ngoại lệ của UC.
- Thiếu sót: ghi vào `docs/ui/design-gaps.md` theo quy ước của file.
  - Thiếu nhỏ (trạng thái lỗi/rỗng/khoá, thông báo, hộp xác nhận): dựng bằng component sẵn có, trạng thái "cần duyệt".
  - Thiếu lớn (màn mới, đổi luồng, thêm/bớt trường, hành vi khác SRS): **dừng và hỏi** trước khi làm.
  - Figma trái SRS: làm theo SRS và báo lại. Không bao giờ sửa SRS.

## 3. Dựng màn
- Cấu trúc: `src/features/<module>/application` (use case + interface repository), `infrastructure/mock` (mock repository, dữ liệu mẫu; fixture dùng chung ở `frontend/packages/fixtures`), `presentation` (screen, component riêng của feature, hook ViewModel).
- Nối repository/use case **chỉ** ở `src/app/container.ts`; thêm route vào `src/app/navigation` (mobile) hoặc `src/app` (admin).
- Chỉ dùng token trong `src/core/ui/theme` và component trong `src/core/ui/components`. Thiếu component dùng chung thì mở rộng component sẵn có hoặc thêm vào `core/ui` kèm mục trong DevUiKit; không tạo bản trùng trong feature.
- Không mã hex, cỡ chữ hay số cứng trong màn. Không chuỗi cứng trong JSX: thêm key i18n vào `vi.json` và `en.json`.
- Form: react-hook-form + zod, lỗi ngay dưới trường (UI-2).
- Mobile: vùng chạm ≥ 44x44 dp (UI-3), có `accessibilityLabel` và `accessibilityRole`. Admin: dùng tốt ở 1366x768 (UI-4), thao tác được bằng bàn phím, có nhãn/aria.
- Không tự tính Sao, streak, tiến độ (BR-23); không hard-code giá trị chỉnh được (MNT-3).
- Số liệu trên Figma (điểm, số câu, số sao) và nút Apple là placeholder.
- Cần dependency mới thì hỏi trước (mobile thêm bằng `npx expo install`).

## 4. Kiểm tra
Trong `frontend/`:
```bash
pnpm typecheck && pnpm lint && pnpm test && pnpm depcruise
```
Logic mới trong `application/` có test (Vitest). Báo kết quả thật, kể cả khi lỗi.

## 5. Cập nhật tài liệu
- `docs/ui/figma-map.md`: điền cột *Đường dẫn code*, đổi *Trạng thái* (`đang làm` / `cần duyệt` / `xong`).
- `docs/ui/design-gaps.md`: cập nhật trạng thái GAP đã xử lý.

## 6. Báo cáo (tiếng Việt)
- Đã làm gì: file chính, component mới hoặc mở rộng, key i18n.
- Bảng trạng thái màn: trạng thái nào có frame Figma, trạng thái nào tự dựng (GAP).
- GAP mới, câu hỏi cần người dùng quyết định.
- Chưa kiểm chứng: chưa chạy trên thiết bị/simulator, chưa thử offline, chưa nối API thật.
- Chưa commit; đề xuất commit theo Conventional Commits (ví dụ `feat(mobile): add login screen`).
