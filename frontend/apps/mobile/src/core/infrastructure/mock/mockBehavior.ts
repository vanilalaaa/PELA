import { create } from 'zustand';
import { ApiRequestError, CLIENT_ERROR_CODES } from '../http/ApiRequestError';

/** Chế độ của mọi Mock repository, chỉnh từ màn DevUiKit (chỉ bản dev). */
export type MockMode = 'normal' | 'error' | 'empty';

export const MOCK_LATENCY_MIN_MS = 300;
export const MOCK_LATENCY_MAX_MS = 800;

interface DevMockSettings {
  mode: MockMode;
  setMode: (mode: MockMode) => void;
}

export const useDevMockSettings = create<DevMockSettings>((set) => ({
  mode: 'normal',
  setMode: (mode) => set({ mode }),
}));

/** Độ trễ giả lập trong [300, 800] ms; `random` ∈ [0, 1) inject được để test. */
export function pickMockLatency(random: number): number {
  const r = Math.min(Math.max(random, 0), 1);
  return Math.round(MOCK_LATENCY_MIN_MS + r * (MOCK_LATENCY_MAX_MS - MOCK_LATENCY_MIN_MS));
}

export interface MockBehaviorOptions {
  getMode?: () => MockMode;
  sleep?: (ms: number) => Promise<void>;
  random?: () => number;
}

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Bọc một lời gọi mock: chờ 300–800 ms rồi trả dữ liệu, hoặc ép lỗi/rỗng theo chế độ dev.
 * `empty` là giá trị "rỗng" hợp lệ của lời gọi đó (vd. [] hoặc null).
 */
export async function mockCall<T>(produce: () => T, empty: T, options: MockBehaviorOptions = {}): Promise<T> {
  const { getMode = () => useDevMockSettings.getState().mode, sleep = defaultSleep, random = Math.random } = options;
  await sleep(pickMockLatency(random()));
  const mode = getMode();
  if (mode === 'error') {
    throw new ApiRequestError(503, CLIENT_ERROR_CODES.mockForced, 'Mock forced error (dev setting)');
  }
  if (mode === 'empty') return empty;
  return produce();
}
