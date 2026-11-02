# 0004 — WatermelonDB 0.28.0 trên Android: bật BuildConfig, tạm tắt JSI

- Trạng thái: Đã chấp nhận (31/10/2026) — chỉ áp dụng cho Android; iOS chưa kiểm chứng
- Liên quan: CO-2, 0003-offline-first-outbox

## Bối cảnh

Mobile dùng Expo SDK 58, React Native 0.88 (New Architecture) và Continuous Native Generation: `android/`, `ios/` không commit, mọi thay đổi native phải đi qua config plugin trong `app.json`. WatermelonDB cần native module nên chỉ chạy trên dev build.

Ngày 30/10/2026 đã thử hai cách và đều không build được Android:

1. **`@morrowdigital/watermelondb-expo-plugin@2.3.3` + `@nozbe/watermelondb@0.27.1`, `jsi: true`.**
   - Gói không khai báo peerDependencies, build với `@expo/config-plugins` ^8 (thời SDK 51), không có dấu hiệu hỗ trợ SDK 58.
   - Prebuild chạy được và thêm `:watermelondb-jsi` vào `settings.gradle`, `app/build.gradle`; nhưng trong `MainApplication.kt` chỉ chèn được import, không chèn được phần đăng ký JSI.
   - `./gradlew :app:compileDebugKotlin` lỗi ngay trong mã native của WatermelonDB 0.27.1: `watermelondb-jsi` dùng `JSIModulePackage`/`JSIModuleSpec` (đã bị xoá khỏi RN 0.88), và `nozbe_watermelondb` không tìm thấy `BuildConfig`.
   - Kết luận: lỗi nằm trong thư viện, không phải plugin, nên plugin tự viết cũng không cứu được 0.27.1. Đã gỡ plugin.
2. **`@nozbe/watermelondb@0.28.0`, không plugin.**
   - 0.28.0 bỏ `JSIModulePackage`; `WatermelonDBJSIPackage` thành `ReactPackage` thường.
   - Build vẫn lỗi: `WMDatabaseBridge.java` và `WMDatabaseDriver.java` dùng `BuildConfig.DEBUG` nhưng module không bật `buildFeatures.buildConfig` (từ AGP 8 mặc định tắt).

Thử bật cờ chung `android.defaults.buildfeatures.buildconfig=true` trong `gradle.properties`: Gradle dừng với lỗi "The option 'android.defaults.buildfeatures.buildconfig' is deprecated".

## Quyết định

- Giữ `@nozbe/watermelondb` ở **0.28.0** (pin chính xác). Không quay về 0.27.1.
- Config plugin `frontend/apps/mobile/plugins/withWatermelonDB.js` chèn vào `android/build.gradle` một khối `subprojects` chỉ bật `buildFeatures.buildConfig = true` cho module `nozbe_watermelondb`. Không đụng các module khác.
- Không sửa `node_modules`, không dùng `patch-package`.
- **JSI tạm tắt** (`jsi: false` trong `createSqliteDatabase`). Chưa đăng ký `WatermelonDBJSIPackage`. Dữ liệu offline của PELA nhỏ (vài trăm đến vài nghìn review card), chế độ không JSI đủ dùng. Bật JSI là việc riêng, chỉ làm khi app đã chạy ổn và còn thời gian.

- `expo-doctor` báo `@nozbe/watermelondb` "Untested on New Architecture" và `@nozbe/simdjson` không có metadata trên React Native Directory. Hai gói này được thêm vào `expo.doctor.reactNativeDirectoryCheck.exclude` trong `frontend/apps/mobile/package.json`, vì đã kiểm chứng trực tiếp trên máy thật với New Architecture (mục dưới). Nâng WatermelonDB thì phải kiểm chứng lại trước khi giữ exclude.

## Kiểm chứng

- `./gradlew :app:compileDebugKotlin` build thành công (30/10/2026); `:nozbe_watermelondb:compileDebugJavaWithJavac` compile qua.
- Máy thật Honor NLA-LX1, Android 15, arm64-v8a, dev build, `jsi: false` (30–31/10/2026), dùng màn dev "Kiểm tra DB" (`src/app/dev/DbCheckScreen.tsx`, deep link `pela://dev/db-check`):
  - Ghi một `review_card` rồi đọc lại: đủ 15 field đúng (chuỗi, số thực, số nguyên, boolean, ngày dạng chuỗi, timestamp).
  - `adb shell am force-stop` rồi mở lại app (process mới): đọc lại vẫn đúng đủ 15 field.
  - Lặp lại sau khi nâng `expo` ~58.0.21, `expo-dev-client` ~58.0.17, `react-native` 0.88.1: vẫn qua.
- `npx expo-doctor`: 20/20 qua (với exclude ở trên).
- Chưa kiểm chứng: iOS; emulator x86_64 trên máy Intel quá chậm để chạy ổn định, nên kiểm chứng trên máy thật.

## Hệ quả

- Mỗi lần nâng WatermelonDB, React Native hoặc Expo SDK phải build lại Android và chạy lại màn "Kiểm tra DB" để kiểm tra plugin còn cần và còn đúng.
- Truy vấn đi qua bridge thay vì JSI nên chậm hơn; cần đo lại nếu dữ liệu offline tăng nhiều.
- `babel.config.js` xử lý TypeScript trước decorator legacy; nếu không, field `!` của model làm Metro báo lỗi biên dịch và app crash khi mở. Không thêm `@babel/plugin-transform-class-properties` vào đây vì nó xung đột với private method trong React Native (babel-preset-expo đã tự xử lý class properties).
- Unit test chạy model qua Babel cùng thứ tự với Metro (`vitest.config.ts`: TypeScript, decorator legacy, class properties). Vitest không dùng babel-preset-expo nên tự thêm class properties; nếu để Vite tự biên dịch, field của model che accessor của decorator và test không phản ánh đúng app.
