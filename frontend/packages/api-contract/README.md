# @pela/api-contract

Hợp đồng API dùng chung cho mobile và admin-web, sinh từ [`docs/api/openapi.yaml`](../../../docs/api/openapi.yaml).

## File sinh tự động

`src/schema.d.ts` do `openapi-typescript` sinh ra. **Không sửa tay.** Sửa tay sẽ bị ghi đè ở lần sinh sau và lệch với OpenAPI.

## Quy trình đổi API

1. Sửa `docs/api/openapi.yaml`.
2. Trong `frontend/`, chạy `pnpm gen:api`.
3. Commit **cả** `openapi.yaml` lẫn `src/schema.d.ts` trong cùng PR (AGENTS.md mục 8).
4. Chạy `pnpm typecheck` để thấy chỗ nào ở mobile/admin-web cần sửa theo.

## Export

| Tên | Là gì |
|---|---|
| `paths`, `components`, `operations` | Type sinh từ OpenAPI |
| `Schemas` | `components['schemas']`, vd. `Schemas['TokenResponse']`, `Schemas['Me']` |
| `ApiError` | Khuôn lỗi `{"error":{"code","message","details"}}` |
| `HealthResponse` | Body của `GET /health` |
| `createPelaClient(baseUrl, middleware?, options?)` | Client có kiểu theo path (bọc `openapi-fetch`) |
| `PelaClient`, `Middleware` | Type đi kèm client |

## Ví dụ

```ts
import { createPelaClient, type Middleware } from '@pela/api-contract';

const bearer: Middleware = {
  async onRequest({ request }) {
    const token = await getAccessToken(); // lấy từ nơi lưu token của app
    if (token) request.headers.set('Authorization', `Bearer ${token}`);
    return request;
  },
};

const client = createPelaClient('http://localhost:8080/v1', [bearer]);
const { data, error } = await client.POST('/auth/login', { body: { email, password } });
```

`baseUrl` đã gồm tiền tố `/v1`. Mobile có sẵn middleware gắn Bearer và làm mới phiên một lần (single-flight) ở `apps/mobile/src/core/infrastructure/http`.

## Lưu ý

- `openapi-typescript@7.13.0` khai báo peer `typescript ^5`; repo dùng TypeScript 6. pnpm sẽ cảnh báo peer, nhưng sinh type và typecheck vẫn chạy đúng. Bỏ qua cảnh báo này cho tới khi `openapi-typescript` hỗ trợ TS 6.
- Tên type theo `components.schemas` của OpenAPI (camelCase).
