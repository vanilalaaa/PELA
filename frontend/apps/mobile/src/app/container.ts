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
import { createRememberableTokenStore } from '../core/infrastructure/http/rememberableTokenStore';
import { createSecureKeyValueStore } from '../core/infrastructure/storage/secureKeyValueStore';
import type { UseCases } from './UseCasesContext';
import { createAuthUseCases } from '../features/auth/application';
import { createApiAuthRepository } from '../features/auth/infrastructure/ApiAuthRepository';
import { createMockAuthRepository } from '../features/auth/infrastructure/MockAuthRepository';
import { createWritingUseCases } from '../features/writing/application';
import { createApiWritingRepository } from '../features/writing/infrastructure/ApiWritingRepository';
import { createMockWritingRepository } from '../features/writing/infrastructure/MockWritingRepository';
import { createOnboardingUseCases } from '../features/onboarding/application/onboardingUseCases';
import { createMockOnboardingRepository } from '../features/onboarding/infrastructure/MockOnboardingRepository';
import { createWatermelonSurveyDraftStore } from '../features/onboarding/infrastructure/WatermelonSurveyDraftStore';
import { INITIAL_ONBOARDING, writeMockOnboarding } from '../core/infrastructure/mock';
import { createPlacementUseCases } from '../features/placement/application/placementUseCases';
import { createMockPlacementRepository } from '../features/placement/infrastructure/MockPlacementRepository';
import { createRoadmapUseCases } from '../features/roadmap/application/roadmapUseCases';
import { createMockRoadmapRepository } from '../features/roadmap/infrastructure/MockRoadmapRepository';
import { createLessonUseCases } from '../features/lesson/application/lessonUseCases';
import { createMockLessonRepository } from '../features/lesson/infrastructure/MockLessonRepository';
import { createFinalTestUseCases } from '../features/finalTest/application/finalTestUseCases';
import { createMockFinalTestRepository } from '../features/finalTest/infrastructure/MockFinalTestRepository';
import { createChallengeUseCases } from '../features/challenge/application/challengeUseCases';
import { createMockChallengeRepository } from '../features/challenge/infrastructure/MockChallengeRepository';
import { createListeningUseCases } from '../features/listening/application/listeningUseCases';
import { createMockListeningRepository } from '../features/listening/infrastructure/MockListeningRepository';
import { createProfileUseCases } from '../features/profile/application/profileUseCases';
import { createMockProfileRepository } from '../features/profile/infrastructure/MockProfileRepository';
import { createSocialUseCases } from '../features/social/application/socialUseCases';
import { createMockSocialRepository } from '../features/social/infrastructure/MockSocialRepository';
import { createGamificationUseCases } from '../features/gamification/application/gamificationUseCases';
import { createMockGamificationRepository } from '../features/gamification/infrastructure/MockGamificationRepository';
import { createSettingsUseCases } from '../features/settings/application/settingsUseCases';
import { createMockSettingsRepository } from '../features/settings/infrastructure/MockSettingsRepository';
import { createLevelUseCases } from '../features/level/application/levelUseCases';
import { createMockLevelRepository } from '../features/level/infrastructure/MockLevelRepository';
import { createSyncUseCases } from '../features/sync/application/syncUseCases';
import { createMockSyncRepository } from '../features/sync/infrastructure/MockSyncRepository';
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
/** Token phiên: secure-store khi "Ghi nhớ đăng nhập", ngược lại chỉ trong bộ nhớ (Auth.Login.Remember). */
const tokenStore = createRememberableTokenStore(createSecureTokenStore());
/** Nơi mock lưu trạng thái cần còn sau khi mở lại app (vd. onboarding) — cùng secure-store với phiên. */
const mockStore = createSecureKeyValueStore();

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

/**
 * Chọn repository theo nguồn dữ liệu. Tính năng chưa có Api repository (chưa có endpoint, xem
 * docs/api-requests.md) luôn dùng mock dù bảng ghi 'api'.
 */
function pick<R>(feature: FeatureName, mock: () => R, api?: (client: ApiClient['client']) => R): R {
  if (dataSourceOf(feature) === 'api' && api && apiClient) return api(apiClient.client);
  if (__DEV__ && dataSourceOf(feature) === 'api' && !api) {
    console.warn(`[container] ${feature}: chưa có Api repository, dùng mock`);
  }
  return mock();
}

/** Bản nháp khảo sát (ROB-1) trên WatermelonDB localStorage. */
const surveyDrafts = createWatermelonSurveyDraftStore(getDatabase);

/**
 * Chỉ dùng ở DevUiKit (__DEV__): đưa onboarding mock về surveyPending và xoá nháp khảo sát, giữ phiên.
 * Caller tự chuyển session sang nhánh Onboarding.
 */
export async function resetMockOnboardingForDev(): Promise<void> {
  if (!__DEV__) return;
  await writeMockOnboarding(mockStore, INITIAL_ONBOARDING);
  await surveyDrafts.clear();
}

/** Use case của mọi tính năng, đã tiêm repository (mock hoặc API). */
export const useCases: UseCases = {
  auth: createAuthUseCases(pick('auth', createMockAuthRepository, createApiAuthRepository), tokenStore),
  onboarding: createOnboardingUseCases(pick('onboarding', () => createMockOnboardingRepository(mockStore)), surveyDrafts),
  placement: createPlacementUseCases(pick('placement', () => createMockPlacementRepository(mockStore))),
  roadmap: createRoadmapUseCases(pick('roadmap', createMockRoadmapRepository)),
  lesson: createLessonUseCases(pick('lesson', createMockLessonRepository)),
  finalTest: createFinalTestUseCases(pick('finalTest', createMockFinalTestRepository)),
  challenge: createChallengeUseCases(pick('challenge', createMockChallengeRepository)),
  writing: createWritingUseCases(pick('writing', createMockWritingRepository, createApiWritingRepository)),
  listening: createListeningUseCases(pick('listening', createMockListeningRepository)),
  profile: createProfileUseCases(pick('profile', createMockProfileRepository)),
  social: createSocialUseCases(pick('social', createMockSocialRepository)),
  gamification: createGamificationUseCases(pick('gamification', createMockGamificationRepository)),
  settings: createSettingsUseCases(pick('settings', createMockSettingsRepository)),
  sync: createSyncUseCases(pick('sync', createMockSyncRepository)),
  level: createLevelUseCases(pick('level', createMockLevelRepository)),
};
