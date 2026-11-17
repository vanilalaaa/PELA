import { describe, expect, it, vi } from 'vitest';
import type { AuthRepository, SessionTokenPort } from './AuthRepository';
import { createAuthUseCases } from './authUseCases';
import type { AuthTokens, Me } from './types';

const ME = { userId: 'u1', email: 'a@b.c', fullName: 'A', role: 'learner', status: 'active', timezone: 'Asia/Ho_Chi_Minh' } as Me;
const TOKENS = { accessToken: 'a', refreshToken: 'r' };

/** Nơi lưu token giả trong bộ nhớ (test không import infrastructure). */
function createMemoryTokenStore(initial: AuthTokens | null = null): SessionTokenPort {
  let current = initial;
  return {
    get: async () => current,
    set: async (t) => {
      current = t;
    },
    clear: async () => {
      current = null;
    },
  };
}

function repo(over: Partial<AuthRepository> = {}): AuthRepository {
  return {
    register: vi.fn(),
    login: vi.fn(async () => TOKENS),
    logout: vi.fn(async () => undefined),
    getMe: vi.fn(async () => ME),
    requestPasswordReset: vi.fn(),
    verifyPasswordOtp: vi.fn(),
    resetPassword: vi.fn(),
    ...over,
  };
}

describe('auth use cases', () => {
  it('login lưu token rồi trả /me', async () => {
    const tokens = createMemoryTokenStore();
    const auth = createAuthUseCases(repo(), tokens);
    expect(await auth.login({ email: 'a@b.c', password: 'Abcdef12' })).toEqual(ME);
    expect(await tokens.get()).toEqual(TOKENS);
  });
  it('login truyền chế độ ghi nhớ xuống nơi lưu token', async () => {
    const set = vi.fn(async () => undefined);
    const tokens: SessionTokenPort = { get: async () => null, set, clear: async () => undefined };
    const auth = createAuthUseCases(repo(), tokens);
    await auth.login({ email: 'a@b.c', password: 'Abcdef12' }, { remember: false });
    expect(set).toHaveBeenLastCalledWith(TOKENS, { persist: false });
    await auth.login({ email: 'a@b.c', password: 'Abcdef12' });
    expect(set).toHaveBeenLastCalledWith(TOKENS, { persist: true });
  });
  it('login sai → không lưu token', async () => {
    const tokens = createMemoryTokenStore();
    const auth = createAuthUseCases(repo({ login: () => Promise.reject(new Error('INVALID_CREDENTIALS')) }), tokens);
    await expect(auth.login({ email: 'a@b.c', password: 'x' })).rejects.toThrow();
    expect(await tokens.get()).toBeNull();
  });
  it('logout xoá token khi server ok', async () => {
    const tokens = createMemoryTokenStore(TOKENS);
    await createAuthUseCases(repo(), tokens).logout();
    expect(await tokens.get()).toBeNull();
  });
  it('logout vẫn xoá token khi server lỗi / mất mạng', async () => {
    const tokens = createMemoryTokenStore(TOKENS);
    const auth = createAuthUseCases(repo({ logout: () => Promise.reject(new Error('offline')) }), tokens);
    await expect(auth.logout()).rejects.toThrow('offline');
    expect(await tokens.get()).toBeNull();
  });
  it('restoreSession: không token → null, không gọi /me', async () => {
    const r = repo();
    expect(await createAuthUseCases(r, createMemoryTokenStore()).restoreSession()).toBeNull();
    expect(r.getMe).not.toHaveBeenCalled();
  });
});
