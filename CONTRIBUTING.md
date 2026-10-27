# Hướng dẫn đóng góp — PELA

Tài liệu này mô tả quy trình làm việc chung của nhóm. Quy tắc dành cho AI agent nằm ở [AGENTS.md](AGENTS.md).

## Mục lục

1. [Prerequisites](#prerequisites)
2. [Tech Stack](#tech-stack)
3. [Git Workflow](#git-workflow)
4. [Commit Convention](#commit-convention)
5. [Code Review Checklist](#code-review-checklist)
6. [Getting Started](#getting-started)
7. [Project Structure](#project-structure)
8. [Development Workflow](#development-workflow)
9. [Code Style](#code-style)
10. [Testing](#testing)
11. [Definition of Done](#definition-of-done)
12. [Commands Reference](#commands-reference)
13. [Môi trường & Lưu ý](#môi-trường--lưu-ý)

---

## Prerequisites

| Công cụ | Ghi chú |
|---|---|
| JDK 21 | Temurin hoặc Oracle JDK. Gradle dùng wrapper `./gradlew`, không cần cài riêng. |
| Node.js LTS | Bản 24 trở lên. |
| pnpm 12 | `npm install -g pnpm@12.15.0` (khớp `packageManager` trong `frontend/package.json`). |
| Android Studio / Xcode | Để tạo dev build. Xcode chỉ có trên macOS. |
| Docker | Để chạy test Testcontainers ở máy local (CI đã có sẵn). |
| Git | |
| Tài khoản Supabase | Xin invite vào organization của nhóm. |
| Gemini API key | Mỗi người tự tạo trong Google AI Studio, không dùng chung. |

## Tech Stack

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security (OAuth2 Resource Server, JWT Supabase), JdbcTemplate/jOOQ, Flyway, Gradle (Kotlin DSL) |
| Test backend | JUnit 5, ArchUnit, Testcontainers (PostgreSQL 17 + pgvector), JaCoCo |
| Mobile | React Native + Expo (dev build), TypeScript strict, React Navigation, Zustand, React Hook Form + Zod, i18next, WatermelonDB |
| Admin web | React + TypeScript (Vite), TanStack Query, React Router, Zustand, React Hook Form + Zod |
| Frontend chung | pnpm workspace, Vitest, dependency-cruiser, oxlint |
| Hạ tầng | Supabase (PostgreSQL 17 + pgvector, Auth, Storage), Google Gemini, FCM/APNs |

## Git Workflow

```text
main      ← bản nộp, protected, chỉ merge từ develop khi chốt phiên bản
 └─ develop   ← nhánh tích hợp, chỉ merge qua PR
     ├─ feature/{van|uyen}-{mo-ta}
     ├─ bugfix/{van|uyen}-{mo-ta}
     └─ hotfix/{mo-ta}
```

Ví dụ: `feature/van-srs-review-api`, `bugfix/uyen-outbox-retry`, `hotfix/login-crash`.

Quy trình:

1. Cập nhật `develop`: `git switch develop && git pull`.
2. Tạo nhánh: `git switch -c feature/van-srs-review-api`.
3. Commit nhỏ, đúng [Commit Convention](#commit-convention).
4. Push và mở PR với **base = `develop`**.
5. Người còn lại review và **approve** trước khi merge.
6. Merge bằng **squash merge**. Xoá nhánh sau khi merge.

**Tiêu đề PR phải theo [Commit Convention](#commit-convention)** (ví dụ `feat(srs): add review submission use case`), vì khi squash GitHub lấy tiêu đề PR làm commit message trên `develop`.

Mô tả PR phải ghi mã UC/BR liên quan (ví dụ `UC-24, BR-7`). Dùng template có sẵn trong `.github/PULL_REQUEST_TEMPLATE.md`.

Đưa `develop` lên `main` (chốt bản nộp): mở PR `develop` → `main` và merge bằng **merge commit**, không squash. Squash ở bước này làm lịch sử `main` và `develop` lệch nhau, lần merge sau sẽ ra conflict giả.

Không commit trực tiếp và không force-push lên `main`/`develop`.

## Commit Convention

[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): message` — tiếng Anh, thì hiện tại, chữ thường đầu câu, không dấu chấm cuối.

| Type | Dùng khi |
|---|---|
| `feat` | Thêm tính năng |
| `fix` | Sửa lỗi |
| `refactor` | Đổi cấu trúc, không đổi hành vi |
| `docs` | Tài liệu |
| `style` | Định dạng, không đổi logic |
| `test` | Thêm/sửa test |
| `chore` | Cấu hình, dependency, việc vặt |
| `ci` | GitHub Actions |

Scope: tên module backend (`identity`, `srs`, `writing`, `rag`, …) hoặc `mobile`, `web`, `domain`, `db`, `docs`, `ci`.

```text
feat(srs): add review submission use case
fix(mobile): retry outbox items after reconnect
chore(db): add V3 migration for listening_attempt
```

Không thêm trailer `Co-Authored-By` hay dòng "Generated with …" của công cụ AI. Repo đã tắt sẵn cho Claude Code trong `.claude/settings.json`; công cụ khác thì tự tắt trong cấu hình trên máy mình.

File cấu hình agent dùng chung (`AGENTS.md`, `CLAUDE.md`, `.claude/settings.json`) được commit. Cấu hình cá nhân để ở `CLAUDE.local.md` và `.claude/settings.local.json` (đã gitignore).

## Code Review Checklist

Người mở PR tự kiểm trước, reviewer kiểm lại:

- [ ] Build pass (backend + frontend).
- [ ] Test pass, có test cho phần thay đổi.
- [ ] Không có secret trong diff (key, token, `.env`, mật khẩu).
- [ ] Không vi phạm ranh giới tầng: ArchUnit và `pnpm depcruise` pass.
- [ ] Không hard-code chuỗi hiển thị (dùng i18n) và màu (dùng theme token).
- [ ] Logic nghiệp vụ nằm ở domain/use case, không nằm trong controller hay component.
- [ ] Mọi thao tác ghi khi offline đi qua outbox.
- [ ] Đổi API thì đã cập nhật `docs/api/openapi.yaml`.
- [ ] Ngưỡng/prompt AI đọc từ `system_setting` / `prompt_template`.

## Getting Started

### 1. Clone và biến môi trường

```bash
git clone <repo-url> SE121-PELA
cd SE121-PELA
cp .env.example .env
```

Điền `.env` bằng thông tin của **project Supabase dev riêng** (xem mục 3). Không commit `.env`.

### 2. Backend

```bash
cd backend
# nạp biến môi trường từ ../.env vào shell (ví dụ: set -a; source ../.env; set +a)
./gradlew build      # compile + test
./gradlew bootRun    # Flyway tự chạy migration trong ../db/migrations khi khởi động
```

Kiểm tra `GET http://localhost:8080/v1/health` → `{"status":"ok"}`.

Flyway đọc migration từ `filesystem:../db/migrations` (đổi bằng biến `FLYWAY_LOCATIONS`). Chạy app từ thư mục `backend/`.

Migration **chỉ chạy bằng Flyway**. Không dùng `supabase db push` / `supabase migration` của Supabase CLI.

### 3. Supabase

- Mỗi người dùng **một project dev riêng**. Không chạy migration hay seed lên project đang dùng để test/demo.
- Bật extension `vector` (pgvector) trong project.
- `DB_URL`, `DB_USER`, `DB_PASSWORD`: lấy trong nút **Connect** của project. Dùng **Session pooler (cổng 5432)** hoặc **Direct connection**. **Không dùng Transaction pooler (cổng 6543)** — Flyway sẽ lỗi.
- `SUPABASE_JWT_ISSUER`: `https://<project-ref>.supabase.co/auth/v1`
- `SUPABASE_JWKS_URL`: `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`
- Project **phải bật JWT Signing Keys** (khoá bất đối xứng). Backend chỉ xác thực JWT qua JWKS, không hỗ trợ Legacy JWT secret (HS256).
  1. Project Settings → **JWT Keys**.
  2. Nếu đang ở chế độ Legacy JWT secret: chuyển sang Signing Keys, tạo khoá mới và bật lên.
  3. Mở `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` — phải trả về ít nhất một khoá.
  
  Đổi khoá làm các phiên đăng nhập cũ hết hiệu lực; với project dev chỉ cần đăng nhập lại.

### 4. Frontend

```bash
cd frontend
pnpm install
pnpm --filter mobile android      # build + cài dev build lên emulator/thiết bị Android
pnpm --filter mobile ios          # macOS
pnpm --filter mobile start        # chỉ chạy Metro khi đã cài dev build
pnpm --filter admin-web dev       # http://localhost:5173
```

App mobile dùng **dev build**, không dùng Expo Go.

## Project Structure

```text
backend/src/main/java/com/pela/
└─ <module>/                   shared, identity, onboarding, assessment, roadmap, srs,
   │                           challenge, rag, writing, listening, gamification, social,
   │                           notification, sync, admin, platform
   │                           (cấu hình Spring dùng chung: platform/config/)
   ├─ domain/                  entity, value object, luật nghiệp vụ — Java thuần
   ├─ application/
   │  ├─ port/in/              interface use case (API công khai của module)
   │  ├─ port/out/             interface tới DB, AI, push…
   │  └─ usecase/              hiện thực use case
   └─ adapter/
      ├─ in/web/               REST controller /v1
      ├─ in/job/               scheduler
      └─ out/                  JdbcTemplate, Gemini, Supabase, FCM…

frontend/
├─ apps/mobile/src/
│  ├─ app/container.ts         composition root duy nhất
│  ├─ core/infrastructure/     hạ tầng dùng chung (DB cục bộ, API client, Supabase…)
│  ├─ domain/                  logic riêng của app (logic dùng chung ở packages/domain)
│  └─ features/<module>/       trùng tên module backend
│     ├─ application/          use case, port — không import React/infrastructure/presentation
│     ├─ infrastructure/       hiện thực port
│     └─ presentation/         screen, component, ViewModel hook
├─ apps/admin-web/src/         core/infrastructure, domain, features/<module>/… (như mobile)
├─ packages/domain/            TS thuần dùng chung (SM-2, Star…)
├─ packages/api-contract/      DTO + Zod từ OpenAPI
├─ packages/fixtures/          test vector JSON dùng chung với backend
└─ .dependency-cruiser.cjs     luật ranh giới tầng

db/migrations/                 Flyway V{n}__*.sql
db/seed/                       dữ liệu dev
docs/                          SRS, kiến trúc, sơ đồ, OpenAPI, ADR
```

## Development Workflow

### 1. Thêm use case backend

Thứ tự: **domain → port → use case → adapter → controller → test**. Ví dụ "nộp kết quả ôn từ vựng" trong module `srs`:

```java
// domain/ReviewCard.java — luật SM-2, Java thuần
public record ReviewCard(UUID id, double easinessFactor, int repetitions, int intervalDays) {
    public ReviewCard apply(boolean correct) { /* BR-26 */ }
}

// application/port/in/SubmitReviewUseCase.java
public interface SubmitReviewUseCase {
    void submit(UUID userId, List<ReviewAnswer> answers);
}

// application/port/out/ReviewCardRepository.java
public interface ReviewCardRepository {
    Optional<ReviewCard> find(UUID userId, UUID cardId);
    void save(UUID userId, ReviewCard card);
}

// application/usecase/SubmitReviewService.java — điều phối, không biết JDBC
// adapter/out/persistence/JdbcReviewCardRepository.java — implements ReviewCardRepository
// adapter/in/web/ReviewController.java — lấy userId từ JWT, gọi SubmitReviewUseCase
```

Test: unit test cho `ReviewCard` (dùng `sm2-cases.json`) và `SubmitReviewService` (fake repository); Testcontainers cho `JdbcReviewCardRepository`.

### 2. Thêm màn hình mobile

Thứ tự: **use case trong `features/<module>/application/` → ViewModel hook → screen**.

```ts
// src/features/srs/application/submitReview.ts — không import React / WatermelonDB
export function makeSubmitReview(deps: { outbox: OutboxPort; clock: ClockPort }) {
  return async (answers: ReviewAnswer[]) => { /* ghi vào outbox */ };
}

// src/features/srs/presentation/useReviewViewModel.ts — hook nhận use case đã tiêm
// src/features/srs/presentation/ReviewScreen.tsx — chỉ render, chuỗi qua t('review.title')
// src/app/container.ts — tạo adapter WatermelonDB và tiêm vào makeSubmitReview
```

### 3. Thêm bảng

1. Tạo migration mới: `db/migrations/V{n}__add_listening_attempt.sql` (`snake_case`, khoá chính UUID). Không sửa migration đã merge.
2. Nếu bảng dùng offline: cập nhật schema và migration WatermelonDB trong `apps/mobile/src/core/infrastructure/`.
3. Commit với scope `db`: `chore(db): add listening_attempt table`.

### 4. Thêm prompt AI

- Thêm bản ghi `prompt_template` (key, version, nội dung, JSON schema output) **bằng migration**. Admin portal không có màn quản lý prompt/cấu hình (ngoài phạm vi R1.0).
- Không viết prompt trong code. Code chỉ tham chiếu key của prompt; version đang dùng là cấu hình.
- Output LLM được validate theo JSON schema; luôn có fallback.

## Code Style

- **Chung**: `.editorconfig` (UTF-8, LF, 2 space; Java 4 space).
- **Java**: package `com.pela.<module>.<layer>`; `PascalCase` cho class, `camelCase` cho method/field; use case: `XxxUseCase` + `XxxService`; không dùng field injection, dùng constructor injection.
- **TypeScript**: strict; component `PascalCase.tsx`, còn lại `camelCase.ts`; hook `useXxx`.
- **UI**: chuỗi hiển thị qua i18next (vi/en); màu, khoảng cách qua theme token.
- **API**: lỗi theo khuôn `{"error":{"code","message","details"}}`; `userId` lấy từ JWT.
- **DB**: `snake_case`, UUID.

## Testing

- **Bắt buộc** unit test cho domain + application, mục tiêu độ phủ **≥ 70%** (PER-7). Báo cáo JaCoCo: `backend/build/reports/jacoco/`.
- **Test vector dùng chung**: logic có ở cả client và server (SM-2, Star, sinh câu hỏi, đếm từ) phải có case trong `frontend/packages/fixtures/*.json`, chạy ở cả Vitest và JUnit.
- **Repository**: Testcontainers với image `pgvector/pgvector:pg17` cho repository dùng pgvector / SQL đặc thù.
- **Stub Supabase khi test**: `schema.sql` tham chiếu `auth.users` (`id`, `email`, `raw_user_meta_data`), `auth.uid()`, role `authenticated` và `anon` — những thứ chỉ Supabase có. Vì vậy test chạy thêm `backend/src/test/resources/db/stub/V0_1__supabase_auth_stub.sql` (trước V1) để giả lập. Schema dùng thêm đối tượng Supabase nào khác thì bổ sung vào file stub này, không sửa migration thật.
- **Kiến trúc**: `ArchitectureTest` (ArchUnit) và `pnpm depcruise` chạy trong CI.
- **E2E**: Detox (mobile) và Playwright (admin web) cho các luồng chính trước buổi demo.

## Definition of Done

- [ ] Merged vào `develop` qua PR đã được review.
- [ ] CI xanh.
- [ ] Có test cho phần thay đổi.
- [ ] Đã cập nhật OpenAPI nếu đổi API.
- [ ] Đã cập nhật tài liệu / ADR nếu đổi quyết định.
- [ ] Tính năng có dữ liệu học: đã chạy thử offline → online và kiểm tra đồng bộ.

## Commands Reference

| Việc | Lệnh |
|---|---|
| Build + test backend | `cd backend && ./gradlew build` |
| Chỉ test backend | `cd backend && ./gradlew test` |
| Chạy backend | `cd backend && ./gradlew bootRun` |
| Cài frontend | `cd frontend && pnpm install` |
| Typecheck | `pnpm typecheck` |
| Test frontend | `pnpm test` |
| Lint | `pnpm lint` |
| Ranh giới tầng | `pnpm depcruise` |
| Dev build Android | `pnpm --filter mobile android` |
| Dev build iOS | `pnpm --filter mobile ios` |
| Metro | `pnpm --filter mobile start` |
| Thêm thư viện mobile | `cd frontend/apps/mobile && npx expo install <pkg>` |
| Kiểm tra cấu hình Expo | `cd frontend/apps/mobile && npx expo-doctor` |
| Admin web dev | `pnpm --filter admin-web dev` |
| Admin web build | `pnpm --filter admin-web build` |

## Môi trường & Lưu ý

- Không commit `.env`, Gemini API key, Supabase service role key, keystore. `.gitignore` đã chặn sẵn; vẫn phải tự kiểm diff.
- Chỉ **backend** giữ service role key. Mobile và admin web chỉ dùng **anon key** (biến `EXPO_PUBLIC_*`, `VITE_*` sẽ bị đóng gói vào client — không đặt secret vào đó).
- Mỗi người dùng Gemini key và project Supabase dev riêng.
- Project Supabase gói Free tự **tạm dừng sau khoảng 1 tuần không dùng**. Backend không kết nối được DB thì vào Dashboard kiểm tra và bấm Restore trước khi tìm lỗi khác.
- Không log PII (email, tên, nội dung bài viết, token).
- Lỡ commit secret: báo nhóm ngay và **thu hồi/đổi key** — xoá commit là không đủ.
