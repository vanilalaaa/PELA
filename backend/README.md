# PELA backend

Spring Boot 3 / Java 21, modular monolith Clean Architecture (`com.pela.<module>.<layer>`). Quy tắc chung: [AGENTS.md](../AGENTS.md), quy trình: [CONTRIBUTING.md](../CONTRIBUTING.md).

## Chạy

```bash
./gradlew bootRun          # profile dev mặc định; đọc backend/.env (xem ../.env.example)
```

Windows PowerShell dùng `.\gradlew.bat` thay cho `./gradlew`.

## Test

| Lệnh | Chạy gì |
|---|---|
| `./gradlew build` | Compile, mọi unit test, ArchUnit, test tích hợp Testcontainers (cần Docker đang chạy), JaCoCo. **Không** gồm test `@Tag("external")`. |
| `./gradlew test` | Như trên, không đóng gói. |
| `./gradlew externalTest` | Chỉ test `@Tag("external")`: gọi dịch vụ thật, tốn quota, không chạy trong CI. |

### Test external (Gemini thật)

`GeminiExternalTest` gọi Gemini thật để xác nhận định dạng request/response của 3 adapter (`generateContent` structured output, `embedContent` 768 chiều, TTS qua Interactions API).

1. Có Gemini API key: biến môi trường `GEMINI_API_KEY`, hoặc dòng `GEMINI_API_KEY=` trong `backend/.env`. Test không in key ra.
2. Tuỳ chọn đổi model (mặc định theo seed: `gemini-3.8-flash` từ V6, `gemini-embedding-001`): `GEMINI_TEXT_MODEL`, `GEMINI_EMBEDDING_MODEL`.
3. Kiểm TTS cần đặt `GEMINI_TTS_MODEL` (model mặc định của hệ thống là `gemini-3.8-flash-tts`, seed ở migration V5; `gemini-3.8-flash-lite-tts` là dự phòng). Không đặt thì test TTS được bỏ qua, hai test còn lại vẫn chạy.
4. Hai biến thể request TTS đang chờ xác nhận: `GEMINI_TTS_VARIANT` = `SPEECH_CONFIG` (mặc định, theo hướng dẫn) hoặc `STRUCTURED_SPEECH_CONFIG` (theo tham chiếu). Chạy cả hai rồi gửi kết quả; biến thể sai sẽ bị xoá.
5. Chạy trên Windows (PowerShell, trong thư mục `backend/`):

```powershell
$env:GEMINI_API_KEY = "<key>"            # bỏ qua nếu đã có trong backend\.env
$env:GEMINI_TTS_MODEL = "gemini-3.8-flash-tts"
$env:GEMINI_TTS_VARIANT = "SPEECH_CONFIG"   # rồi chạy lại với STRUCTURED_SPEECH_CONFIG
.\gradlew.bat externalTest --rerun-tasks
```

`--rerun-tasks` để Gradle không bỏ qua vì "up-to-date" khi chỉ đổi biến môi trường. Lỗi từ Gemini được log dạng `HTTP <mã> <status> <message>` (không log key, không log nội dung bài viết).

File audio sinh ra được lưu ở `backend/build/external-test-tts.wav` để nghe thử.

## AI (shared ports, adapter ở `platform/adapter/out`)

- `SettingsPort` (system_setting, cache 5 phút), `PromptTemplatePort` (prompt_template active, cache 5 phút, render `{{x}}`).
- `LlmPort` → Gemini `generateContent` + structured output; `EmbeddingPort` → `embedContent`, vector chuẩn hoá L2; `TtsPort` → Interactions API; `ContentFilterPort` → danh sách từ cấm trong `src/main/resources/content-filter/`.
- Model, ngưỡng an toàn, số chiều đọc từ system_setting (`ai.*`); không hard-code. Không log prompt hay bài viết của learner.
