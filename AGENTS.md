# AGENTS.md

Quy tắc cho mọi AI agent (Claude Code, Codex, Cursor, …) làm việc trong repo PELA. Bắt buộc tuân theo.

## 1. Project snapshot

- PELA — ứng dụng học tiếng Anh cá nhân hoá (CEFR, SM-2, Challenge, Writing chấm bằng AI, RAG tạo đề Writing, Listening, gamification, offline-first). Đồ án SE121 — UIT.
- `backend/`: Java 21 + Spring Boot 3, REST `/v1`, modular monolith Clean Architecture, package `com.pela`, 16 module.
- `frontend/`: pnpm workspace — `apps/mobile` (React Native + Expo dev build), `apps/admin-web` (React + Vite), `packages/domain`, `packages/api-contract`, `packages/fixtures`.
- Hạ tầng: Supabase (PostgreSQL 17 + pgvector, Auth, Storage). AI: Gemini `gemini-2.5-flash`, `gemini-embedding-001` (768 chiều). Push: FCM/APNs.
- Nguồn sự thật: `docs/srs/`, `docs/architecture/`, `docs/api/openapi.yaml`, `db/migrations/`.

## 2. Source of truth & thứ tự ưu tiên

- SRS > tài liệu kiến trúc > OpenAPI > code hiện có.
- Khi các nguồn mâu thuẫn, hoặc yêu cầu không rõ: **dừng lại và hỏi**. Không tự chọn, không tự đoán.
- Mã UC/BR/SEC/MNT/PER trong code và PR phải trích đúng từ SRS.

## 3. Quyết định đã chốt

Không được thay đổi khi chưa có xác nhận rõ ràng của người dùng.

1. **RAG chỉ cho Writing** (UC-24, BR-7): chỉ dùng cho "Để AI tạo đề Writing mới" và phát hiện đề trùng khi Admin nhập kho đề. Truy hồi lọc bắt buộc theo topic + level, ngưỡng 0.65; ngưỡng cache 0.92; lỗi hoặc dưới ngưỡng → trả đề có sẵn.
2. **Challenge theo luật**: Daily/Weekly Challenge sinh theo luật, không gọi AI, chạy được offline.
3. **Server là trọng tài** (BR-23): Star, streak, SM-2, kết quả bài thi luôn tính lại từ đáp án thô. Client gửi đáp án theo lô kèm UUID do client sinh để gửi lại an toàn.
4. **SM-2** (BR-26): đúng → q=5, sai → q=2, EF sàn 1.3; sai 3 lần liên tiếp → EF = 1.3 và `needs_reinforcement = true`.
5. **Star** (BR-36, BR-20): 0–5 sao/phiên (Weekly tối đa 6). Làm lại Lesson/bài nghe chỉ cộng phần tăng so với kết quả tốt nhất. Final Test 3 lượt, trượt hết thì khoá, cần 30 sao để mở 1 lượt.
6. **Cấu hình, không hard-code** (MNT-3, MNT-4): ngưỡng, giới hạn, tên model nằm ở `system_setting`; prompt nằm ở `prompt_template`.

Các lựa chọn công nghệ ở mục 1 (React Navigation, WatermelonDB, JdbcTemplate/jOOQ, Gemini…) cũng đã chốt.

## 4. Quy tắc kiến trúc

- Phụ thuộc hướng vào trong: `adapter` → `application` → `domain`.
- Backend, mỗi module: `domain/`, `application/port/in`, `application/port/out`, `application/usecase`, `adapter/in/web`, `adapter/in/job`, `adapter/out/...`.
- `domain` không import Spring, `java.sql`/JDBC, adapter. Frontend domain không import React, React Native, Expo, WatermelonDB, Supabase hay bất kỳ thư viện ngoài nào.
- `application` không import `adapter` (backend) / `infrastructure`, `presentation`, React (frontend).
- Module chỉ gọi module khác qua `application/port/in` của module đó. Không truy cập bảng, domain hay adapter của module khác.
- Controller và component/screen không chứa logic nghiệp vụ: chỉ map dữ liệu, gọi use case / ViewModel.
- Mọi phụ thuộc ngoài (DB, Gemini, Supabase, FCM, clock, UUID) đi qua port/interface.
- Không dùng JPA/Hibernate. Truy cập DB bằng JdbcTemplate (hoặc jOOQ) trong `adapter/out`.
- Mobile có duy nhất một composition root: `frontend/apps/mobile/src/app/container.ts`.
- Ranh giới được kiểm tự động: `backend/src/test/java/com/pela/ArchitectureTest.java` (ArchUnit) và `frontend/.dependency-cruiser.cjs`. Không nới lỏng các luật này để code qua được.

## 5. Quy tắc dữ liệu

- Mọi thay đổi schema qua migration Flyway mới `db/migrations/V{n}__{mo_ta}.sql`. Không sửa migration đã merge.
- Bảng/cột `snake_case`; khoá chính UUID.
- Bảng dùng offline phải cập nhật schema + migration WatermelonDB tương ứng.
- Ghi kết quả học dùng id do client sinh + ràng buộc unique để idempotent.
- Không tin số liệu client gửi (điểm, Star, streak, EF, interval…): server tính lại từ đáp án thô.
- Mọi thao tác ghi khi offline đi qua bảng `outbox`.
- Migration chỉ chạy bằng Flyway; không dùng `supabase db push` / `supabase migration`.
- Test dùng stub `backend/src/test/resources/db/stub/V0_1__supabase_auth_stub.sql` thay cho schema `auth` của Supabase. Test lỗi vì thiếu đối tượng Supabase thì bổ sung vào stub; không bao giờ sửa `V1__init.sql` cho vừa test.

## 6. Quy tắc AI

- Tên model, ngưỡng, giới hạn đọc từ `system_setting`. Không hard-code trong code.
- Prompt lưu ở `prompt_template` có version. Không viết prompt trong code.
- Output LLM phải theo JSON schema và được validate trước khi dùng; không hợp lệ thì coi là lỗi.
- Luôn có fallback khi AI lỗi, timeout hoặc dưới ngưỡng (BR-7).
- Lọc nội dung do AI sinh trước khi hiển thị cho người dùng (BR-15).
- Không gọi Gemini từ client; chỉ backend gọi qua port/out.

## 7. Bảo mật

- Không bao giờ đọc, in ra, commit hay gửi đi secret (`.env`, Gemini key, Supabase service role key, JWT secret, keystore).
- Chỉ dùng `.env.example` với giá trị trống. Không tạo `.env` có giá trị.
- Chỉ backend giữ service role key; client chỉ dùng anon key.
- Không log PII (email, tên, nội dung bài viết, token).
- `userId` lấy từ JWT, không lấy từ body/query (SEC-4).
- JWT xác thực qua JWKS (Supabase JWT Signing Keys). Không thêm đường xác thực HS256 / JWT secret.

## 8. Quy ước code

- Java: package `com.pela.<module>.<layer>`, class `PascalCase`, method/field `camelCase`, hằng `UPPER_SNAKE_CASE`. Use case: `XxxUseCase` (port/in) + `XxxService` (usecase).
- TypeScript: strict; file `camelCase.ts`, component `PascalCase.tsx`, hook `useXxx`.
- Không hard-code chuỗi hiển thị: dùng i18next (vi/en). Không hard-code màu: dùng theme token.
- Lỗi API theo khuôn: `{"error":{"code":"UPPER_SNAKE_CASE","message":"...","details":...}}` (`components.schemas.Error` trong OpenAPI). Ném exception trong `com.pela.shared.domain.exception` (`NotFound` 404, `Conflict` 409, `Forbidden` 403, `Locked` 423, `TooManyRequests` 429, `ValidationFailed` 422); `GlobalExceptionHandler` lo việc map. Không tự dựng `ResponseEntity` lỗi trong controller.
- userId của người gọi: tham số `@CurrentUserId UUID userId` trong controller (lấy từ claim `sub` của JWT).
- Thời gian và id do server sinh: inject `ClockPort` / `IdGeneratorPort`; không gọi `Instant.now()`, `LocalDate.now()`, `UUID.randomUUID()` trong domain/application (ArchUnit chặn).
- Đổi API → cập nhật `docs/api/openapi.yaml` trong cùng PR.
- Logic dùng chung client/server (SM-2, Star, sinh câu hỏi, đếm từ) phải có test vector trong `frontend/packages/fixtures`, dùng cho cả Vitest và JUnit.

## 9. Git

- Conventional Commits: `type(scope): message`, tiếng Anh, thì hiện tại. Type: `feat, fix, refactor, docs, style, test, chore, ci`. Scope: tên module backend hoặc `mobile`, `web`, `domain`, `db`, `docs`, `ci`.
- Branch: `feature/{van|uyen}-{mo-ta}`, `bugfix/{van|uyen}-{mo-ta}`, `hotfix/{mo-ta}`; PR vào `develop`.
- Không thêm trailer `Co-Authored-By` hay dòng "Generated with …" của công cụ AI vào commit/PR.
- Không commit trực tiếp lên `main`/`develop`. Không force-push lên `main`/`develop`.
- Không push hay mở PR nếu người dùng chưa yêu cầu.

## 10. Lệnh thường dùng

Backend (trong `backend/`):

```bash
./gradlew build          # compile + test (gồm ArchUnit) + jacoco
./gradlew test           # chỉ test
./gradlew bootRun        # chạy app, mặc định profile dev (cần biến môi trường, xem .env.example)
```

Frontend (trong `frontend/`):

```bash
pnpm install
pnpm typecheck                   # tsc mọi package
pnpm test                        # vitest
pnpm lint                        # oxlint (admin-web)
pnpm depcruise                   # kiểm tra ranh giới tầng
pnpm --filter mobile android     # dev build Android
pnpm --filter admin-web dev      # chạy admin web
pnpm --filter admin-web build
```

Mobile: thêm thư viện bằng `npx expo install <pkg>` (trong `frontend/apps/mobile`). Xem thêm `frontend/apps/mobile/AGENTS.md`.

## 11. Khi làm việc

- Đọc file và tài liệu liên quan trước khi sửa.
- Thay đổi nhỏ, tập trung; mỗi thay đổi logic có test.
- Chạy build/test liên quan trước khi báo xong; báo kết quả thật, kể cả khi lỗi.
- Hỏi trước khi: xoá file, reset DB, chạy migration trên Supabase thật, thêm/đổi/nâng cấp dependency lớn, đổi quyết định ở mục 3.
- Không viết tính năng ngoài phạm vi được yêu cầu.
- Báo rõ cái gì chưa kiểm chứng (chưa chạy, chưa test trên thiết bị, chưa test offline).
