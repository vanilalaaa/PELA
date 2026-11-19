import { describe, expect, it, vi } from 'vitest';
import { ApiRequestError } from '../http/ApiRequestError';
import { MOCK_LATENCY_MAX_MS, MOCK_LATENCY_MIN_MS, mockCall, pickMockLatency, type MockMode } from './mockBehavior';

const opts = (mode: MockMode) => ({ getMode: () => mode, sleep: vi.fn(async () => undefined), random: () => 0.5 });

describe('mockCall', () => {
  it('normal: trả dữ liệu sau độ trễ', async () => {
    const o = opts('normal');
    expect(await mockCall(() => [1, 2], [], o)).toEqual([1, 2]);
    expect(o.sleep).toHaveBeenCalledWith(550);
  });
  it('empty: trả giá trị rỗng', async () => {
    expect(await mockCall(() => [1, 2], [], opts('empty'))).toEqual([]);
  });
  it('error: ném ApiRequestError MOCK_FORCED_ERROR (thử lại được)', async () => {
    const e = await mockCall(() => 1, 0, opts('error')).catch((x: unknown) => x);
    expect(e).toBeInstanceOf(ApiRequestError);
    expect((e as ApiRequestError).code).toBe('MOCK_FORCED_ERROR');
  });
  it('độ trễ trong [300, 800] ms', () => {
    expect(pickMockLatency(0)).toBe(MOCK_LATENCY_MIN_MS);
    expect(pickMockLatency(1)).toBe(MOCK_LATENCY_MAX_MS);
    expect(pickMockLatency(-5)).toBe(MOCK_LATENCY_MIN_MS);
  });
});
