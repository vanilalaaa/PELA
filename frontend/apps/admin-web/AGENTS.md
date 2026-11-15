Quy tắc chung của repo nằm ở [`/AGENTS.md`](../../../AGENTS.md) và được ưu tiên hơn file này.

## Ngôn ngữ

- Tài liệu trong repo (AGENTS.md mobile/admin, `docs/ui/*`, `docs/api-requests.md`, báo cáo đánh giá) và câu trả lời cho người dùng: **tiếng Việt**.
- Code, tên biến, tên file, key i18n, tên nhánh, commit message: **tiếng Anh** (Conventional Commits).

## Lệnh

```bash
pnpm --filter admin-web dev
pnpm --filter admin-web build
pnpm --filter admin-web lint
pnpm --filter admin-web typecheck
```

## Cấu trúc

- Stack: React + Vite + TypeScript, react-router, TanStack Query, Zustand, react-hook-form + zod.
- `src/app/`: khởi động app, router, provider, layout. Composition root duy nhất: `src/app/container.ts`. Chỉ nơi này khởi tạo infrastructure và chọn mock hay repository thật.
- `src/core/ui/`: theme token và component dùng chung. `src/core/infrastructure/`: hạ tầng chung (HTTP client, auth, i18n).
- Mỗi feature trùng tên module backend: `src/features/<module>/`
  - `application/`: use case + interface repository (port). Không import React, infrastructure, presentation.
  - `infrastructure/`: hiện thực port (API client). Mock repository đặt ở `infrastructure/mock/`.
  - `presentation/`: page, component riêng của feature, hook ViewModel (TanStack Query gọi use case). Không import infrastructure.
- `src/domain/`: tuỳ chọn, chỉ cho logic riêng của Admin; logic dùng chung ở `frontend/packages/domain`.
- Fixture dùng chung: `frontend/packages/fixtures`.

## Quy tắc UI

- Figma là nguồn chuẩn cho hình thức (UI-1); `docs/srs/SRS_1.md` là nguồn chuẩn cho nghiệp vụ.
- Trước khi dựng trang: lấy design context và ảnh chụp của node qua Figma MCP. Không đoán khoảng cách, màu hay nội dung chữ.
- Code Figma MCP trả về chỉ để tham khảo: chuyển sang stack, token và component của dự án, không dán nguyên.
- Ưu tiên desktop: dùng tốt ở 1366x768 trở lên (UI-4). Không cần responsive cho mobile ở R1.0. Bảng dài thì cuộn trong vùng nội dung, không cuộn ngang cả trang.
- Chỉ dùng theme token (`src/core/ui`, dạng CSS variable). Không mã hex, cỡ chữ hay số cứng trong trang.
- Dùng lại component trong `src/core/ui`; mở rộng component có sẵn thay vì tạo bản trùng.
- Mọi trang có đủ trạng thái loading, empty, error và mất kết nối.
- Form: react-hook-form + zod, báo lỗi ngay dưới trường (UI-2).
- Thao tác được bằng bàn phím (focus thấy rõ); phần tử tương tác có nhãn (`aria-label` hoặc label gắn với input) và role đúng.
- Mọi chuỗi hiển thị qua i18next. R1.0 chỉ cần tiếng Việt (en để sau). Không viết chuỗi cứng trong JSX. Dependency i18next thêm ở bước dựng nền Admin.
- Trang chỉ gọi use case / interface repository; dữ liệu lấy từ mock repository cho đến khi có API thật.
- UI không tự tính Sao, streak hay quy tắc tiến độ (BR-23). Giá trị có thể chỉnh (MNT-3) không hard-code.
- Hỏi trước khi thêm bất kỳ dependency nào (thư viện UI, bảng, biểu đồ…).
- Số liệu trên Figma chỉ là placeholder.

## Thiếu sót thiết kế

Đối chiếu mỗi trang với UC (luồng chính, luồng phụ, ngoại lệ) và BR trong SRS.

- Ghi từng chỗ thiếu vào [`docs/ui/design-gaps.md`](../../../docs/ui/design-gaps.md).
- **Thiếu nhỏ** (trạng thái lỗi/rỗng/khoá, thông báo, hộp xác nhận): dựng bằng component sẵn có, đánh dấu "cần duyệt".
- **Thiếu lớn** (trang mới, đổi luồng, thêm/bớt trường, hành vi khác SRS): dừng lại và hỏi người dùng.
- Figma trái SRS: làm theo SRS và báo lại. Không bao giờ sửa SRS.
