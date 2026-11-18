/** Cặp token của phiên đăng nhập (TokenResponse của /auth/login, /auth/refresh). */
export interface SessionTokens {
  accessToken: string;
  refreshToken: string;
}

/** Nơi lưu token. Bản thật: expo-secure-store (secureTokenStore.ts); test: bộ nhớ. */
export interface TokenStore {
  get(): Promise<SessionTokens | null>;
  set(tokens: SessionTokens): Promise<void>;
  clear(): Promise<void>;
}

export function createMemoryTokenStore(initial: SessionTokens | null = null): TokenStore {
  let tokens = initial;
  return {
    get: async () => tokens,
    set: async (next) => {
      tokens = next;
    },
    clear: async () => {
      tokens = null;
    },
  };
}
