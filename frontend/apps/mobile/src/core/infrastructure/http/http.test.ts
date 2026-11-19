import { describe, expect, it, vi } from 'vitest';
import type { Middleware } from '@pela/api-contract';
import { ApiRequestError, parseRetryAfter, toApiRequestError, toNetworkError } from './ApiRequestError';
import { createAuthMiddleware, isPublicAuthPath } from './authMiddleware';
import { resolveApiBaseUrl } from './config';
import { createRefreshCoordinator } from './refreshCoordinator';
import { createMemoryTokenStore, type SessionTokens } from './tokenStore';

const OLD: SessionTokens = { accessToken: 'old-access', refreshToken: 'old-refresh' };
const NEW: SessionTokens = { accessToken: 'new-access', refreshToken: 'new-refresh' };

describe('ApiRequestError', () => {
  it('đọc khuôn lỗi {"error":{...}}', () => {
    const e = toApiRequestError(409, { error: { code: 'EMAIL_ALREADY_REGISTERED', message: 'taken', details: { f: 1 } } });
    expect(e).toBeInstanceOf(ApiRequestError);
    expect([e.status, e.code, e.message, e.details]).toEqual([409, 'EMAIL_ALREADY_REGISTERED', 'taken', { f: 1 }]);
    expect(e.retryable).toBe(false);
  });
  it('body rỗng hoặc sai khuôn → HTTP_<status>', () => {
    expect(toApiRequestError(502, null).code).toBe('HTTP_502');
    expect(toApiRequestError(500, { message: 'x' }).code).toBe('HTTP_500');
    expect(toApiRequestError(503, undefined).retryable).toBe(true);
  });
  it('lỗi mạng và timeout có status 0, thử lại được', () => {
    const net = toNetworkError(new TypeError('Network request failed'));
    const timeout = toNetworkError(Object.assign(new Error('t'), { name: 'AbortError' }));
    expect([net.status, net.code, net.retryable]).toEqual([0, 'NETWORK_ERROR', true]);
    expect(timeout.code).toBe('TIMEOUT');
  });
});

describe('resolveApiBaseUrl', () => {
  it('ghép /v1, bỏ dấu / cuối, không lặp /v1', () => {
    expect(resolveApiBaseUrl('http://10.0.0.2:8080/')).toBe('http://10.0.0.2:8080/v1');
    expect(resolveApiBaseUrl('http://h:8080/v1')).toBe('http://h:8080/v1');
  });
  it('trống → null (dùng mock)', () => {
    expect(resolveApiBaseUrl(undefined)).toBeNull();
    expect(resolveApiBaseUrl('  ')).toBeNull();
  });
});

describe('refreshCoordinator (single-flight)', () => {
  it('nhiều lời gọi đồng thời chỉ refresh một lần và nhận cùng token', async () => {
    const tokenStore = createMemoryTokenStore(OLD);
    let resolve!: (t: SessionTokens) => void;
    const requestRefresh = vi.fn(() => new Promise<SessionTokens>((r) => (resolve = r)));
    const c = createRefreshCoordinator({ tokenStore, requestRefresh, onSessionExpired: vi.fn() });
    const all = Promise.all([c.refresh(), c.refresh(), c.refresh()]);
    await vi.waitFor(() => expect(requestRefresh).toHaveBeenCalled());
    resolve(NEW);
    expect(await all).toEqual([NEW, NEW, NEW]);
    expect(requestRefresh).toHaveBeenCalledTimes(1);
    expect(requestRefresh).toHaveBeenCalledWith('old-refresh');
    expect(await tokenStore.get()).toEqual(NEW);
  });
  it('refresh bị từ chối (401) → xoá token, báo hết phiên đúng một lần', async () => {
    const tokenStore = createMemoryTokenStore(OLD);
    const onSessionExpired = vi.fn();
    const c = createRefreshCoordinator({
      tokenStore,
      requestRefresh: () => Promise.reject(new ApiRequestError(401, 'INVALID_REFRESH_TOKEN', 'x')),
      onSessionExpired,
    });
    expect(await Promise.all([c.refresh(), c.refresh()])).toEqual([null, null]);
    expect(onSessionExpired).toHaveBeenCalledTimes(1);
    expect(await tokenStore.get()).toBeNull();
  });
  it('lỗi mạng khi refresh → giữ token, không đăng xuất', async () => {
    const tokenStore = createMemoryTokenStore(OLD);
    const onSessionExpired = vi.fn();
    const c = createRefreshCoordinator({
      tokenStore,
      requestRefresh: () => Promise.reject(toNetworkError(new TypeError('offline'))),
      onSessionExpired,
    });
    expect(await c.refresh()).toBeNull();
    expect(onSessionExpired).not.toHaveBeenCalled();
    expect(await tokenStore.get()).toEqual(OLD);
  });
  it('lần refresh sau chạy lại (không giữ promise cũ)', async () => {
    const tokenStore = createMemoryTokenStore(OLD);
    const requestRefresh = vi.fn(() => Promise.resolve(NEW));
    const c = createRefreshCoordinator({ tokenStore, requestRefresh, onSessionExpired: vi.fn() });
    await c.refresh();
    await c.refresh();
    expect(requestRefresh).toHaveBeenCalledTimes(2);
  });
});

// ---- authMiddleware: gọi trực tiếp các hook như openapi-fetch.
type Params = Parameters<NonNullable<Middleware['onRequest']>>[0];
function ctx(schemaPath: string, id: string, request: Request): Params {
  return { request, schemaPath, id, params: {}, options: {} } as unknown as Params;
}
async function runRequest(m: Middleware, schemaPath: string, id: string, url = 'http://api/v1/me') {
  const req = new Request(url, { method: 'POST', body: JSON.stringify({ a: 1 }) });
  const out = (await m.onRequest?.(ctx(schemaPath, id, req))) as Request | undefined;
  return out ?? req;
}
async function runResponse(m: Middleware, schemaPath: string, id: string, request: Request, status: number) {
  const response = new Response(null, { status });
  const res = await m.onResponse?.({ ...ctx(schemaPath, id, request), response } as Parameters<NonNullable<Middleware['onResponse']>>[0]);
  return (res as Response | undefined) ?? response;
}

describe('authMiddleware', () => {
  it('gắn Bearer cho endpoint cần xác thực', async () => {
    const m = createAuthMiddleware({ tokenStore: createMemoryTokenStore(OLD), coordinator: { refresh: vi.fn() } });
    const req = await runRequest(m, '/me', '1');
    expect(req.headers.get('Authorization')).toBe('Bearer old-access');
  });
  it('401 → refresh rồi thử lại đúng một lần với token mới, giữ nguyên body', async () => {
    const fetchFn = vi.fn(async (r: Request) => {
      expect(r.headers.get('Authorization')).toBe('Bearer new-access');
      expect(await r.text()).toBe('{"a":1}');
      return new Response('{}', { status: 200 });
    });
    const refresh = vi.fn(async () => NEW);
    const m = createAuthMiddleware({ tokenStore: createMemoryTokenStore(OLD), coordinator: { refresh }, fetchFn: fetchFn as unknown as typeof fetch });
    const req = await runRequest(m, '/me', '2');
    const res = await runResponse(m, '/me', '2', req, 401);
    expect(res.status).toBe(200);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });
  it('thử lại vẫn 401 → trả 401, không lặp', async () => {
    const fetchFn = vi.fn(async () => new Response(null, { status: 401 }));
    const refresh = vi.fn(async () => NEW);
    const m = createAuthMiddleware({ tokenStore: createMemoryTokenStore(OLD), coordinator: { refresh }, fetchFn: fetchFn as unknown as typeof fetch });
    const req = await runRequest(m, '/me', '3');
    const res = await runResponse(m, '/me', '3', req, 401);
    expect(res.status).toBe(401);
    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(refresh).toHaveBeenCalledTimes(1);
  });
  it('refresh thất bại → trả nguyên 401, không thử lại', async () => {
    const fetchFn = vi.fn();
    const m = createAuthMiddleware({ tokenStore: createMemoryTokenStore(OLD), coordinator: { refresh: async () => null }, fetchFn: fetchFn as unknown as typeof fetch });
    const req = await runRequest(m, '/me', '4');
    expect((await runResponse(m, '/me', '4', req, 401)).status).toBe(401);
    expect(fetchFn).not.toHaveBeenCalled();
  });
  it.each(['/auth/login', '/auth/register', '/auth/refresh', '/auth/password/forgot', '/auth/password/reset'])(
    '%s công khai: không gắn Bearer, 401 không refresh',
    async (path) => {
      expect(isPublicAuthPath(path)).toBe(true);
      const refresh = vi.fn();
      const m = createAuthMiddleware({ tokenStore: createMemoryTokenStore(OLD), coordinator: { refresh } });
      const req = await runRequest(m, path, path);
      expect(req.headers.get('Authorization')).toBeNull();
      expect((await runResponse(m, path, path, req, 401)).status).toBe(401);
      expect(refresh).not.toHaveBeenCalled();
    },
  );
  it('/auth/logout cần token (không công khai)', () => {
    expect(isPublicAuthPath('/auth/logout')).toBe(false);
  });
});

describe('Retry-After', () => {
  const now = Date.parse('2026-11-18T10:00:00Z');

  it('đọc số giây', () => {
    expect(parseRetryAfter('30', now)).toBe(30);
    expect(parseRetryAfter(' 0 ', now)).toBe(0);
  });

  it('đọc HTTP-date, làm tròn lên và không âm', () => {
    expect(parseRetryAfter('Wed, 18 Nov 2026 10:00:45 GMT', now)).toBe(45);
    expect(parseRetryAfter('Wed, 18 Nov 2026 09:59:00 GMT', now)).toBe(0);
  });

  it('thiếu hoặc sai định dạng thì null', () => {
    expect(parseRetryAfter(null, now)).toBeNull();
    expect(parseRetryAfter('', now)).toBeNull();
    expect(parseRetryAfter('soon', now)).toBeNull();
    expect(parseRetryAfter('-5', now)).toBeNull();
  });

  it('toApiRequestError gắn retryAfterSec từ header', () => {
    const body = { error: { code: 'TOO_MANY_REQUESTS', message: 'Slow down' } };
    const e = toApiRequestError(429, body, new Headers({ 'Retry-After': '12' }));
    expect(e.code).toBe('TOO_MANY_REQUESTS');
    expect(e.retryAfterSec).toBe(12);
    expect(toApiRequestError(429, body).retryAfterSec).toBeNull();
  });
});
