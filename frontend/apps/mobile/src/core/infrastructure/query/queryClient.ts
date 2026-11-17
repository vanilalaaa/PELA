import { QueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '../http/ApiRequestError';

export const QUERY_STALE_TIME_MS = 30_000;
export const QUERY_MAX_RETRIES = 2;

/**
 * Query chỉ thử lại lỗi mạng/5xx, tối đa 2 lần; 4xx (vd. 401, 422) không thử lại.
 * Lỗi không phải ApiRequestError (bug code) cũng không thử lại.
 */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  return failureCount < QUERY_MAX_RETRIES && error instanceof ApiRequestError && error.retryable;
}

/**
 * QueryClient dùng chung cho dữ liệu server. Mutation KHÔNG tự thử lại (retry: 0): ghi dữ liệu
 * như nộp bài Writing không được gửi lặp; ghi khi offline đi qua outbox (AGENTS.md mục 5).
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: QUERY_STALE_TIME_MS,
        retry: shouldRetryQuery,
        refetchOnWindowFocus: true,
        refetchOnReconnect: true,
      },
      mutations: { retry: 0 },
    },
  });
}
