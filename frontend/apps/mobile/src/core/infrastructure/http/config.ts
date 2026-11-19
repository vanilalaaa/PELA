/** Tiền tố mọi endpoint (OpenAPI: "Mọi endpoint nằm dưới tiền tố /v1"). */
export const API_PREFIX = '/v1';

/**
 * Ghép base URL từ EXPO_PUBLIC_API_BASE_URL (vd. http://192.168.1.5:8080) với /v1.
 * Trả `null` khi chưa cấu hình để container chuyển sang mock thay vì gọi nhầm địa chỉ.
 */
export function resolveApiBaseUrl(raw: string | undefined): string | null {
  const trimmed = raw?.trim().replace(/\/+$/, '');
  if (!trimmed) return null;
  return trimmed.endsWith(API_PREFIX) ? trimmed : `${trimmed}${API_PREFIX}`;
}

/** Đọc biến môi trường public của Expo (được nhúng lúc build Metro). */
export function readApiBaseUrl(): string | null {
  return resolveApiBaseUrl(process.env.EXPO_PUBLIC_API_BASE_URL);
}
