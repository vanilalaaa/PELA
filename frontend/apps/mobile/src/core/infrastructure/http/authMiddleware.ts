import type { Middleware } from '@pela/api-contract';
import type { RefreshCoordinator } from './refreshCoordinator';
import type { TokenStore } from './tokenStore';

/**
 * Endpoint /auth/* công khai: không gắn Bearer, không refresh khi 401 (vd. login sai mật khẩu
 * trả 401 INVALID_CREDENTIALS — không phải phiên hết hạn). /auth/logout cần token nên không ở đây.
 */
export function isPublicAuthPath(schemaPath: string): boolean {
  return (
    schemaPath === '/auth/login' ||
    schemaPath === '/auth/register' ||
    schemaPath === '/auth/refresh' ||
    schemaPath.startsWith('/auth/password/')
  );
}

export interface AuthMiddlewareDeps {
  tokenStore: TokenStore;
  coordinator: RefreshCoordinator;
  /** Inject để test; mặc định fetch toàn cục. */
  fetchFn?: typeof fetch;
}

/**
 * Middleware openapi-fetch: gắn `Authorization: Bearer`; gặp 401 thì làm mới phiên (single-flight)
 * và thử lại request đúng một lần. Request thử lại gọi thẳng fetch nên không đi lại middleware →
 * không lặp vô hạn; nếu vẫn 401 thì trả nguyên response cho caller.
 */
export function createAuthMiddleware({ tokenStore, coordinator, fetchFn }: AuthMiddlewareDeps): Middleware {
  // Bản sao request trước khi gửi (body chỉ đọc được một lần) để thử lại sau refresh.
  const pending = new Map<string, Request>();
  const doFetch = (req: Request) => (fetchFn ?? fetch)(req);

  return {
    async onRequest({ request, schemaPath, id }) {
      if (isPublicAuthPath(schemaPath)) return undefined;
      const tokens = await tokenStore.get();
      if (tokens) request.headers.set('Authorization', `Bearer ${tokens.accessToken}`);
      pending.set(id, request.clone());
      return request;
    },

    async onResponse({ response, schemaPath, id }) {
      const original = pending.get(id);
      pending.delete(id);
      if (response.status !== 401 || isPublicAuthPath(schemaPath) || !original) return undefined;

      const tokens = await coordinator.refresh();
      if (!tokens) return undefined;

      const retry = new Request(original);
      retry.headers.set('Authorization', `Bearer ${tokens.accessToken}`);
      return doFetch(retry);
    },

    onError({ id }) {
      pending.delete(id);
      return undefined;
    },
  };
}
