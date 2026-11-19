import { describe, expect, it } from 'vitest';
import { ApiRequestError } from '../http/ApiRequestError';
import { createQueryClient, QUERY_STALE_TIME_MS, shouldRetryQuery } from './queryClient';

describe('queryClient', () => {
  it('query chỉ thử lại lỗi mạng/5xx, tối đa 2 lần', () => {
    expect(shouldRetryQuery(0, new ApiRequestError(0, 'NETWORK_ERROR', 'x'))).toBe(true);
    expect(shouldRetryQuery(1, new ApiRequestError(503, 'HTTP_503', 'x'))).toBe(true);
    expect(shouldRetryQuery(2, new ApiRequestError(503, 'HTTP_503', 'x'))).toBe(false);
    expect(shouldRetryQuery(0, new ApiRequestError(401, 'UNAUTHORIZED', 'x'))).toBe(false);
    expect(shouldRetryQuery(0, new Error('bug'))).toBe(false);
  });
  it('mutation không thử lại; staleTime 30 giây', () => {
    const d = createQueryClient().getDefaultOptions();
    expect(d.mutations?.retry).toBe(0);
    expect(d.queries?.staleTime).toBe(QUERY_STALE_TIME_MS);
  });
});
