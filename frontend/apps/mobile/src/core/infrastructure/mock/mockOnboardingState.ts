import type { KeyValueStore } from '../storage/keyValueStore';

/**
 * Trạng thái onboarding của mock, lưu cùng nơi với phiên (secure-store) để mở lại app đi đúng màn.
 * Dùng chung cho Mock onboarding và Mock placement (thay cho việc server tự cập nhật khi nộp bài).
 * Kiểu khớp OnboardingStatus của feature onboarding (khai báo lại để mock không phụ thuộc chéo feature).
 */
export type MockCefr = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';
export interface MockOnboardingState {
  onboardingStatus: 'surveyPending' | 'placementPending' | 'done';
  orientedLevel: MockCefr | null;
  cefrLevel: MockCefr | null;
}

const KEY = 'pela.mock.onboarding';
export const INITIAL_ONBOARDING: MockOnboardingState = { onboardingStatus: 'surveyPending', orientedLevel: null, cefrLevel: null };

export async function readMockOnboarding(store: KeyValueStore): Promise<MockOnboardingState> {
  const raw = await store.get(KEY);
  if (!raw) return INITIAL_ONBOARDING;
  try {
    return JSON.parse(raw) as MockOnboardingState;
  } catch {
    return INITIAL_ONBOARDING;
  }
}

export async function writeMockOnboarding(store: KeyValueStore, state: MockOnboardingState): Promise<void> {
  await store.set(KEY, JSON.stringify(state));
}
