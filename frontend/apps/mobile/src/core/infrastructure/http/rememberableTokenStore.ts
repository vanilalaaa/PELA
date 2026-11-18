import type { SessionTokens, TokenStore } from './tokenStore';

export interface SetTokenOptions {
  /** true: lưu bền vào secure-store; false: chỉ giữ trong bộ nhớ (tắt app là mất). */
  persist?: boolean;
}

export interface RememberableTokenStore extends TokenStore {
  set(tokens: SessionTokens, options?: SetTokenOptions): Promise<void>;
}

/**
 * Auth.Login.Remember: có tích "Ghi nhớ đăng nhập" thì token lưu trong secure-store (Keychain/Keystore);
 * không tích thì chỉ giữ trong bộ nhớ và xoá bản đã lưu, nên tắt hẳn app phải đăng nhập lại.
 * Lần làm mới phiên (refresh) không truyền `persist` sẽ giữ đúng chế độ của lần đăng nhập.
 */
export function createRememberableTokenStore(secure: TokenStore): RememberableTokenStore {
  let memory: SessionTokens | null = null;
  // Token đọc được từ secure-store ⇒ phiên đã được ghi nhớ từ trước.
  let persist = true;

  return {
    async get() {
      if (memory) return memory;
      const saved = await secure.get();
      if (saved) {
        memory = saved;
        persist = true;
      }
      return saved;
    },
    async set(tokens, options) {
      if (options?.persist !== undefined) persist = options.persist;
      memory = tokens;
      if (persist) await secure.set(tokens);
      else await secure.clear();
    },
    async clear() {
      memory = null;
      persist = true;
      await secure.clear();
    },
  };
}
