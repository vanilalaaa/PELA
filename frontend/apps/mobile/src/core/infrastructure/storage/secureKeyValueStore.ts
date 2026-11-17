import * as SecureStore from 'expo-secure-store';
import type { KeyValueStore } from './keyValueStore';

/** KeyValueStore trên expo-secure-store (cùng nơi lưu token phiên). Import riêng vì là native. */
export function createSecureKeyValueStore(): KeyValueStore {
  return {
    get: (key) => SecureStore.getItemAsync(key),
    set: (key, value) => SecureStore.setItemAsync(key, value),
    remove: (key) => SecureStore.deleteItemAsync(key),
  };
}
