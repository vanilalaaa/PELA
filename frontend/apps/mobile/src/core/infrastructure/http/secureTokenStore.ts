import * as SecureStore from 'expo-secure-store';
import type { SessionTokens, TokenStore } from './tokenStore';

const ACCESS_KEY = 'pela.auth.accessToken';
const REFRESH_KEY = 'pela.auth.refreshToken';

/** Token lưu trong Keychain/Keystore qua expo-secure-store. Không log giá trị token (AGENTS.md mục 7). */
export function createSecureTokenStore(): TokenStore {
  return {
    async get() {
      const [accessToken, refreshToken] = await Promise.all([
        SecureStore.getItemAsync(ACCESS_KEY),
        SecureStore.getItemAsync(REFRESH_KEY),
      ]);
      return accessToken && refreshToken ? { accessToken, refreshToken } : null;
    },
    async set(tokens: SessionTokens) {
      await Promise.all([
        SecureStore.setItemAsync(ACCESS_KEY, tokens.accessToken),
        SecureStore.setItemAsync(REFRESH_KEY, tokens.refreshToken),
      ]);
    },
    async clear() {
      await Promise.all([SecureStore.deleteItemAsync(ACCESS_KEY), SecureStore.deleteItemAsync(REFRESH_KEY)]);
    },
  };
}
