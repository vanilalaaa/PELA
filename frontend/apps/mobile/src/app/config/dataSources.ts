/**
 * Bảng chọn nguồn dữ liệu cho từng tính năng. Chuyển một tính năng sang API thật = đổi một
 * dòng ở đây (UI không đổi). Chỉ tính năng đã có endpoint trong OpenAPI mới được đặt 'api'.
 */
export const FEATURES = [
  'auth',
  'onboarding',
  'placement',
  'roadmap',
  'lesson',
  'finalTest',
  'challenge',
  'writing',
  'listening',
  'profile',
  'social',
  'gamification',
  'settings',
  'sync',
  'level',
] as const;

export type FeatureName = (typeof FEATURES)[number];
export type DataSource = 'api' | 'mock';

export const dataSources: Record<FeatureName, DataSource> = {
  auth: 'api',
  onboarding: 'mock',
  placement: 'mock',
  roadmap: 'mock',
  lesson: 'mock',
  finalTest: 'mock',
  challenge: 'mock',
  writing: 'api',
  listening: 'mock',
  profile: 'mock',
  social: 'mock',
  gamification: 'mock',
  settings: 'mock',
  sync: 'mock',
  level: 'mock',
};

export interface DataSourceEnv {
  /** EXPO_PUBLIC_DATA_SOURCE: 'mock' ép mọi tính năng dùng mock; giá trị khác bị bỏ qua. */
  forced: string | undefined;
  /** Base URL đã ghép /v1; null = chưa cấu hình backend. */
  apiBaseUrl: string | null;
}

/** Nguồn thực tế của một tính năng: ép mock, hoặc chưa có backend, thì luôn là mock. */
export function resolveDataSource(feature: FeatureName, env: DataSourceEnv, table = dataSources): DataSource {
  if (env.forced?.trim().toLowerCase() === 'mock') return 'mock';
  if (!env.apiBaseUrl) return 'mock';
  return table[feature];
}
