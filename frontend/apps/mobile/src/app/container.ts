/**
 * Composition root duy nhất của app mobile: nơi duy nhất khởi tạo adapter trong
 * `core/infrastructure` và `features/<module>/infrastructure` rồi tiêm vào use case ở
 * `features/<module>/application`. Presentation chỉ nhận use case qua `useUseCases()`.
 *
 * Mock hay API cho từng tính năng: `config/dataSources.ts` + EXPO_PUBLIC_DATA_SOURCE.
 */
import type { Database } from '@nozbe/watermelondb';
import { initI18n } from '../core/infrastructure/i18n';
import { createSqliteDatabase } from '../core/infrastructure/database';
import { createApiClient, readApiBaseUrl, type ApiClient } from '../core/infrastructure/http';
import { createSecureTokenStore } from '../core/infrastructure/http/secureTokenStore';
import type { UseCases } from './UseCasesContext';
import { FEATURES, resolveDataSource, type DataSource, type FeatureName } from './config/dataSources';

let databasePromise: Promise<Database> | null = null;

/** Gọi 1 lần ở App.tsx, trước khi render NavigationContainer. */
export function bootstrap() {
  initI18n();
}

/**
 * Lazy-init WatermelonDB (SQLite, dùng được trên dev build/device thật — KHÔNG hoạt động
 * trong Expo Go vì cần native module). WatermelonDB giữ dữ liệu offline và outbox.
 */
export function getDatabase(): Promise<Database> {
  if (!databasePromise) {
    databasePromise = createSqliteDatabase();
  }
  return databasePromise;
}

// ---------------------------------------------------------------------------------------------
// Phiên đăng nhập: tầng điều hướng (bước d) đăng ký listener để về AuthStack khi refresh thất bại.
// ---------------------------------------------------------------------------------------------
const sessionExpiredListeners = new Set<() => void>();

export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

const apiBaseUrl = readApiBaseUrl();
const tokenStore = createSecureTokenStore();

/** null khi chưa cấu hình EXPO_PUBLIC_API_BASE_URL (mọi tính năng chạy mock). */
const apiClient: ApiClient | null = apiBaseUrl
  ? createApiClient({
      baseUrl: apiBaseUrl,
      tokenStore,
      onSessionExpired: () => sessionExpiredListeners.forEach((listener) => listener()),
    })
  : null;

const dataSourceEnv = { forced: process.env.EXPO_PUBLIC_DATA_SOURCE, apiBaseUrl };

/** Nguồn dữ liệu thực tế của một tính năng (đã tính EXPO_PUBLIC_DATA_SOURCE và base URL). */
export function dataSourceOf(feature: FeatureName): DataSource {
  return resolveDataSource(feature, dataSourceEnv);
}

/** Bảng nguồn thực tế của mọi tính năng (hiển thị ở DevUiKit). */
export function resolvedDataSources(): Record<FeatureName, DataSource> {
  return Object.fromEntries(FEATURES.map((f) => [f, dataSourceOf(f)])) as Record<FeatureName, DataSource>;
}

/** Hạ tầng dùng chung để tạo Api<Feature>Repository (bước c). */
export const infrastructure = { apiClient, tokenStore } as const;

/** Use case của mọi tính năng; bước (c) điền theo `dataSourceOf(feature)`. */
export const useCases: UseCases = {};
