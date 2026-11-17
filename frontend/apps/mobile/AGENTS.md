Quy tắc chung của repo nằm ở [`/AGENTS.md`](../../../AGENTS.md) và được ưu tiên hơn file này.

## Ngôn ngữ

- Tài liệu trong repo (AGENTS.md mobile/admin, `docs/ui/*`, `docs/api-requests.md`, báo cáo đánh giá) và câu trả lời cho người dùng: **tiếng Việt**.
- Code, tên biến, tên file, key i18n, tên nhánh, commit message: **tiếng Anh** (Conventional Commits).

## Expo thay đổi nhanh — đừng tin trí nhớ

1. Đọc major version của `expo` trong `package.json`.
2. Tra tài liệu đúng phiên bản: `https://docs.expo.dev/versions/v<major>.0.0/`, hoặc mục lục https://docs.expo.dev/llms.txt.

## Lệnh

```bash
npx expo install <package>   # luôn dùng để thêm thư viện — chọn phiên bản tương thích SDK
npx expo run:android         # tạo và chạy dev build (không dùng Expo Go)
pnpm --filter mobile start   # Metro cho dev build
pnpm --filter mobile typecheck
npx expo-doctor
```

## Cấu trúc

- Điều hướng dùng **React Navigation** (đã chốt). Không chuyển sang Expo Router.
- Mỗi feature trùng tên module backend: `src/features/<module>/`
  - `application/`: use case + interface repository (port). Không import React, infrastructure, presentation.
  - `infrastructure/`: hiện thực port (API client, WatermelonDB). Mock repository đặt ở `infrastructure/mock/`.
  - `presentation/`: screen, component riêng của feature, ViewModel hook. Không import infrastructure.
- Fixture/test vector dùng chung client–server: `frontend/packages/fixtures`.
- Hạ tầng chung: `src/core/infrastructure`. UI dùng chung: `src/core/ui` (token ở `core/ui/theme`, component ở `core/ui/components`).
- Composition root duy nhất: `src/app/container.ts`. Chỉ nơi này khởi tạo infrastructure và chọn mock hay repository thật; màn chỉ nhận use case/ViewModel đã tiêm.
- Thư mục `ios/` và `android/` được sinh tự động (CNG) và bị gitignore; cấu hình native qua `app.json` và config plugin, không sửa tay.
- Thư viện có native code cần dev build lại (`npx expo run:android|ios`).

## Gọi API

- Chỉ gọi backend qua `createPelaClient` (`@pela/api-contract`) bên trong `features/<f>/infrastructure/Api<Feature>Repository.ts`. Xem [README api-contract](../../packages/api-contract/README.md).
- Không sửa tay `packages/api-contract/src/schema.d.ts`; đổi API thì sửa OpenAPI rồi `pnpm gen:api`.
- Endpoint thiếu so với SRS: ghi vào [`docs/api-requests.md`](../../../docs/api-requests.md), không tự đoán; tính năng đó dùng mock.
- Chọn mock hay API cho từng tính năng ở `src/app/config/dataSources.ts`; `EXPO_PUBLIC_DATA_SOURCE=mock` ép mọi tính năng dùng mock.

## Quy tắc UI

- Figma là nguồn chuẩn cho hình thức (UI-1); `docs/srs/SRS_1.md` (SRS v4.3) là nguồn chuẩn cho nghiệp vụ.
- Trước khi dựng màn: lấy design context và ảnh chụp của node qua Figma MCP. Không đoán khoảng cách, màu hay nội dung chữ.
- Code Figma MCP trả về (thường React + Tailwind) chỉ để tham khảo: chuyển sang stack, token và component của dự án, không dán nguyên.
- Chỉ dùng theme token (`src/core/ui/theme`). Không mã hex, cỡ chữ hay số cứng trong màn.
- Dùng lại `src/core/ui/components`; mở rộng component có sẵn thay vì tạo bản trùng.
- Mọi màn có đủ trạng thái loading, empty, error và offline.
- Form: react-hook-form + zod, báo lỗi ngay dưới trường (UI-2).
- Vùng chạm ≥ 44x44 dp (UI-3); phần tử tương tác có `accessibilityLabel` và `accessibilityRole`.
- Mọi chuỗi hiển thị qua i18next (vi + en). Không viết chuỗi cứng trong JSX.
- Màn chỉ gọi use case / interface repository; dữ liệu lấy từ mock repository cho đến khi có API thật.
- UI không tự tính Sao, streak hay quy tắc tiến độ; chỉ hiển thị giá trị từ use case hoặc server (BR-23). Giá trị có thể chỉnh (MNT-3) không hard-code.
- Hỏi trước khi thêm bất kỳ dependency nào; thêm bằng `npx expo install`.
- Số liệu trên Figma (điểm, số câu, số sao) và nút Apple chỉ là placeholder.

## Thiếu sót thiết kế

Figma có thể thiếu trạng thái hoặc luồng phụ. Đối chiếu mỗi màn với UC (luồng chính, luồng phụ, ngoại lệ) và BR trong SRS.

- Ghi từng chỗ thiếu vào [`docs/ui/design-gaps.md`](../../../docs/ui/design-gaps.md) (id, màn, UC/BR, SRS yêu cầu, Figma đang có, đề xuất, trạng thái).
- **Thiếu nhỏ** trên màn đã có (trạng thái lỗi/rỗng/khoá, thông báo, hộp xác nhận): dựng bằng component sẵn có, đánh dấu "cần duyệt".
- **Thiếu lớn** (màn mới, đổi luồng, thêm/bớt trường, hành vi khác SRS): dừng lại và hỏi người dùng.
- Figma trái SRS: làm theo SRS và báo lại. Không bao giờ sửa SRS.
