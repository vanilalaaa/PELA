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
2. Tuỳ chọn đổi model (mặc định theo seed V3): `GEMINI_TEXT_MODEL`, `GEMINI_EMBEDDING_MODEL`.
3. Kiểm TTS cần chỉ định model, vì `ai.model.tts` chưa chọn: `GEMINI_TTS_MODEL` (vd một model trong bảng "supported models" của trang [Speech generation](https://ai.google.dev/gemini-api/docs/speech-generation)). Không đặt thì test TTS được bỏ qua, hai test còn lại vẫn chạy.
4. Chạy:

```powershell
$env:GEMINI_TTS_MODEL = "<tts-model>"   # tuỳ chọn
.\gradlew.bat externalTest
```

File audio sinh ra được lưu ở `backend/build/external-test-tts.wav` để nghe thử.

## AI (shared ports, adapter ở `platform/adapter/out`)

- `SettingsPort` (system_setting, cache 5 phút), `PromptTemplatePort` (prompt_template active, cache 5 phút, render `{{x}}`).
- `LlmPort` → Gemini `generateContent` + structured output; `EmbeddingPort` → `embedContent`, vector chuẩn hoá L2; `TtsPort` → Interactions API; `ContentFilterPort` → danh sách từ cấm trong `src/main/resources/content-filter/`.
- Model, ngưỡng an toàn, số chiều đọc từ system_setting (`ai.*`); không hard-code. Không log prompt hay bài viết của learner.
