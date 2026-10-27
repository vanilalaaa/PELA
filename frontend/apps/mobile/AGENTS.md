Quy tắc chung của repo nằm ở [`/AGENTS.md`](../../../AGENTS.md) và được ưu tiên hơn file này.

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

## Quy tắc riêng cho mobile

- Điều hướng dùng **React Navigation** (đã chốt). Không chuyển sang Expo Router.
- Tầng: `src/domain` → `src/application` → `src/presentation` / `src/infrastructure`. `src/compositionRoot.ts` là nơi duy nhất khởi tạo infrastructure.
- Thư mục `ios/` và `android/` được sinh tự động (CNG) và bị gitignore; cấu hình native qua `app.json` và config plugin, không sửa tay.
- Thư viện có native code cần dev build lại (`npx expo run:android|ios`).
