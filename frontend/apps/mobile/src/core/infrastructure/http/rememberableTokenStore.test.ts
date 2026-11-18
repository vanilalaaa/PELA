import { describe, expect, it } from 'vitest';
import { createRememberableTokenStore } from './rememberableTokenStore';
import { createMemoryTokenStore } from './tokenStore';

const A = { accessToken: 'a1', refreshToken: 'r1' };
const B = { accessToken: 'a2', refreshToken: 'r2' };

describe('rememberableTokenStore (Auth.Login.Remember)', () => {
  it('tích ghi nhớ → lưu vào secure-store; mở lại app (store mới) vẫn còn phiên', async () => {
    const secure = createMemoryTokenStore();
    await createRememberableTokenStore(secure).set(A, { persist: true });
    expect(await secure.get()).toEqual(A);
    expect(await createRememberableTokenStore(secure).get()).toEqual(A);
  });

  it('không tích → chỉ giữ trong bộ nhớ, không ghi secure-store; tắt app (store mới) phải đăng nhập lại', async () => {
    const secure = createMemoryTokenStore();
    const store = createRememberableTokenStore(secure);
    await store.set(A, { persist: false });
    expect(await store.get()).toEqual(A);
    expect(await secure.get()).toBeNull();
    expect(await createRememberableTokenStore(secure).get()).toBeNull();
  });

  it('không tích → xoá phiên đã ghi nhớ trước đó', async () => {
    const secure = createMemoryTokenStore(A);
    await createRememberableTokenStore(secure).set(B, { persist: false });
    expect(await secure.get()).toBeNull();
  });

  it('refresh (không truyền persist) giữ đúng chế độ của lần đăng nhập', async () => {
    const secure = createMemoryTokenStore();
    const store = createRememberableTokenStore(secure);
    await store.set(A, { persist: false });
    await store.set(B);
    expect(await store.get()).toEqual(B);
    expect(await secure.get()).toBeNull();

    const remembered = createRememberableTokenStore(createMemoryTokenStore());
    await remembered.set(A, { persist: true });
    await remembered.set(B);
    expect(await remembered.get()).toEqual(B);
  });

  it('clear xoá cả bộ nhớ lẫn secure-store', async () => {
    const secure = createMemoryTokenStore();
    const store = createRememberableTokenStore(secure);
    await store.set(A, { persist: true });
    await store.clear();
    expect(await store.get()).toBeNull();
    expect(await secure.get()).toBeNull();
  });
});
