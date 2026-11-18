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
  /** Số giây phải chờ theo header `Retry-After` (thường đi kèm 429); null nếu server không gửi. */
  readonly retryAfterSec: number | null;

  constructor(status: number, code: string, message: string, details?: unknown, retryAfterSec: number | null = null) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.retryAfterSec = retryAfterSec;
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

/**
 * Đọc header `Retry-After` (RFC 9110): số giây, hoặc mốc thời gian HTTP-date. Trả số giây làm tròn lên
 * (≥ 0); không có hoặc sai định dạng thì null.
 */
export function parseRetryAfter(value: string | null | undefined, nowMs: number = Date.now()): number | null {
  if (!value) return null;
  const trimmed = value.trim();
  if (/^\d+$/.test(trimmed)) return Number(trimmed);
  // HTTP-date luôn có tên thứ/tháng; chặn chuỗi như "-5" mà Date.parse hiểu thành năm.
  if (!/[a-z]/i.test(trimmed)) return null;
  const at = Date.parse(trimmed);
  if (Number.isNaN(at)) return null;
  return Math.max(0, Math.ceil((at - nowMs) / 1000));
}

/**
 * Dựng ApiRequestError từ status + body đã parse; body sai khuôn thì dùng mã HTTP_<status>.
 * `headers` (tuỳ chọn) để đọc `Retry-After`.
 */
export function toApiRequestError(status: number, body: unknown, headers?: Pick<Headers, 'get'>): ApiRequestError {
  const retryAfterSec = parseRetryAfter(headers?.get('Retry-After'));
  if (isApiErrorBody(body)) {
    return new ApiRequestError(status, body.error.code, body.error.message, body.error.details ?? undefined, retryAfterSec);
  }
  return new ApiRequestError(status, `HTTP_${status}`, `Request failed with status ${status}`, undefined, retryAfterSec);
}

/** Lỗi ném ra khi fetch thất bại trước khi có response. */
export function toNetworkError(cause: unknown): ApiRequestError {
  const isTimeout = cause instanceof Error && (cause.name === 'AbortError' || cause.name === 'TimeoutError');
  return isTimeout
    ? new ApiRequestError(0, CLIENT_ERROR_CODES.timeout, 'Request timed out')
    : new ApiRequestError(0, CLIENT_ERROR_CODES.network, 'Network request failed');
}
