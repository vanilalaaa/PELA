/**
 * Hợp đồng API dùng chung cho mobile và admin-web.
 *
 * - `schema.d.ts` SINH TỰ ĐỘNG từ docs/api/openapi.yaml bằng `pnpm gen:api`; không sửa tay.
 *   Đổi API: sửa openapi.yaml → chạy `pnpm gen:api` → commit cả hai trong cùng PR.
 * - `createPelaClient` bọc openapi-fetch: gọi API theo path có kiểu, vd.
 *   `client.POST('/auth/login', { body })`.
 */
import createClient, { type Client, type ClientOptions, type Middleware } from 'openapi-fetch';
import type { components, operations, paths } from './schema';

export type { components, operations, paths };
export type { Middleware };

/** Schema theo tên trong `components.schemas` (vd. `Schemas['TokenResponse']`). */
export type Schemas = components['schemas'];

/** Khuôn lỗi chung của API: {"error":{"code","message","details"}} (AGENTS.md mục 8). */
export type ApiError = Schemas['Error'];

/** Body trả về của GET /health. */
export type HealthResponse = paths['/health']['get']['responses'][200]['content']['application/json'];

export type PelaClient = Client<paths>;

/**
 * Tạo client cho `baseUrl` (đã gồm tiền tố `/v1`). Middleware chạy theo thứ tự truyền vào
 * (vd. gắn Bearer, làm mới phiên).
 */
export function createPelaClient(
  baseUrl: string,
  middleware: readonly Middleware[] = [],
  options: Omit<ClientOptions, 'baseUrl'> = {},
): PelaClient {
  const client = createClient<paths>({ ...options, baseUrl });
  for (const m of middleware) client.use(m);
  return client;
}
