import { createPelaClient, type PelaClient, type Schemas } from '@pela/api-contract';
import { toApiRequestError, toNetworkError } from './ApiRequestError';
import { createAuthMiddleware } from './authMiddleware';
import { createRefreshCoordinator, type RefreshCoordinator } from './refreshCoordinator';
import type { SessionTokens, TokenStore } from './tokenStore';

export interface ApiClientDeps {
  baseUrl: string;
  tokenStore: TokenStore;
  onSessionExpired: () => void;
  fetchFn?: typeof fetch;
}

export interface ApiClient {
  client: PelaClient;
  tokenStore: TokenStore;
  coordinator: RefreshCoordinator;
}

/** POST /auth/refresh gọi thẳng (không qua middleware) để không tự refresh lồng nhau. */
export function createRefreshRequest(baseUrl: string, fetchFn: typeof fetch = fetch) {
  return async (refreshToken: string): Promise<SessionTokens> => {
    let response: Response;
    try {
      response = await fetchFn(`${baseUrl}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
    } catch (cause) {
      throw toNetworkError(cause);
    }
    const body: unknown = await response.json().catch(() => null);
    if (!response.ok) throw toApiRequestError(response.status, body, response.headers);
    const tokens = body as Schemas['TokenResponse'];
    return { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken };
  };
}

/** Dựng client có kiểu cho /v1 với middleware gắn token + refresh single-flight. */
export function createApiClient({ baseUrl, tokenStore, onSessionExpired, fetchFn }: ApiClientDeps): ApiClient {
  const coordinator = createRefreshCoordinator({
    tokenStore,
    requestRefresh: createRefreshRequest(baseUrl, fetchFn),
    onSessionExpired,
  });
  const client = createPelaClient(baseUrl, [createAuthMiddleware({ tokenStore, coordinator, fetchFn })], {
    fetch: fetchFn,
  });
  return { client, tokenStore, coordinator };
}

/**
 * Chuyển kết quả `{ data, error, response }` của openapi-fetch thành giá trị hoặc ApiRequestError.
 * Dùng trong Api<Feature>Repository: `unwrap(await client.GET(...))`.
 */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined || !result.response.ok) throw toApiRequestError(result.response.status, result.error, result.response.headers);
  return result.data as T;
}

/** Bọc lời gọi để lỗi mạng (fetch reject) thành ApiRequestError NETWORK_ERROR/TIMEOUT. */
export async function call<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (cause) {
    if (cause instanceof Error && cause.name === 'ApiRequestError') throw cause;
    throw toNetworkError(cause);
  }
}
