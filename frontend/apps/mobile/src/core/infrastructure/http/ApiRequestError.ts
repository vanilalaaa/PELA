import type { ApiError } from '@pela/api-contract';

/** Mã lỗi phía client (không do server trả). */
export const CLIENT_ERROR_CODES = {
  network: 'NETWORK_ERROR',
  timeout: 'TIMEOUT',
  unknown: 'UNKNOWN_ERROR',
  mockForced: 'MOCK_FORCED_ERROR',
} as const;

/**
 * Lỗi gọi API theo khuôn `{"error":{"code","message","details"}}` (AGENTS.md mục 8).
 * `status = 0` khi không tới được server (mất mạng, timeout).
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: unknown;

  constructor(status: number, code: string, message: string, details?: unknown) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.details = details;
  }

  /** Lỗi mạng/5xx có thể thử lại; 4xx thì không. */
  get retryable(): boolean {
    return this.status === 0 || this.status >= 500;
  }
}

function isApiErrorBody(body: unknown): body is ApiError {
  if (typeof body !== 'object' || body === null || !('error' in body)) return false;
  const error = (body as { error: unknown }).error;
  return (
    typeof error === 'object' &&
    error !== null &&
    typeof (error as { code?: unknown }).code === 'string' &&
    typeof (error as { message?: unknown }).message === 'string'
  );
}

/** Dựng ApiRequestError từ status + body đã parse; body sai khuôn thì dùng mã HTTP_<status>. */
export function toApiRequestError(status: number, body: unknown): ApiRequestError {
  if (isApiErrorBody(body)) {
    return new ApiRequestError(status, body.error.code, body.error.message, body.error.details ?? undefined);
  }
  return new ApiRequestError(status, `HTTP_${status}`, `Request failed with status ${status}`);
}

/** Lỗi ném ra khi fetch thất bại trước khi có response. */
export function toNetworkError(cause: unknown): ApiRequestError {
  const isTimeout = cause instanceof Error && (cause.name === 'AbortError' || cause.name === 'TimeoutError');
  return isTimeout
    ? new ApiRequestError(0, CLIENT_ERROR_CODES.timeout, 'Request timed out')
    : new ApiRequestError(0, CLIENT_ERROR_CODES.network, 'Network request failed');
}
