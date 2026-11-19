import type { Me } from '../../features/auth/application';
import type { OnboardingEntry, OnboardingStatus } from '../../features/onboarding/application/types';
import { onboardingEntry } from '../../features/onboarding/application/onboardingUseCases';
import type { SessionSnapshot } from './sessionSnapshot';

/**
 * `stale: true`: chưa xác nhận được với server (lỗi tạm thời), đang dùng ảnh chụp lưu trên máy; caller
 * hiện banner ngoại tuyến và gọi lại khi có mạng.
 */
export type BootstrapResult =
  | { status: 'signedIn'; me: Me; onboardingEntry: OnboardingEntry; stale: boolean }
  | { status: 'signedOut' };

export interface BootstrapDeps {
  /** auth.restoreSession: có token thì /me, không có thì null. */
  restoreSession: () => Promise<Me | null>;
  getOnboardingStatus: () => Promise<OnboardingStatus>;
  /** Xoá token đã lưu (secure-store). */
  clearTokens: () => Promise<void>;
  /** Ảnh chụp phiên lần trước (null nếu chưa có). */
  loadSnapshot: () => Promise<SessionSnapshot | null>;
  clearSnapshot: () => Promise<void>;
}

/** Mã lỗi nghĩa là server từ chối phiên của tài khoản (OpenAPI AccountBlocked). */
const REJECTED_CODES = new Set(['ACCOUNT_SUSPENDED', 'ACCOUNT_PENDING_DELETION']);

/**
 * Server từ chối phiên: 401 (token hỏng, refresh cũng thất bại) hoặc tài khoản bị đình chỉ / chờ xoá.
 * Mọi lỗi khác (mạng, timeout, 5xx, lỗi giả lập) là tạm thời.
 */
export function isSessionRejected(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) return false;
  const e = error as { status?: unknown; code?: unknown };
  return e.status === 401 || (typeof e.code === 'string' && REJECTED_CODES.has(e.code));
}

/**
 * Khôi phục phiên khi mở app.
 * - Có token, /me và trạng thái onboarding ok → signedIn (stale=false).
 * - Không có token → signedOut.
 * - Server từ chối phiên → xoá token + ảnh chụp, signedOut.
 * - Lỗi tạm thời → giữ token; có ảnh chụp đúng tài khoản thì signedIn bằng ảnh chụp (stale=true),
 *   chưa có ảnh chụp thì signedOut (giữ token để lần sau thử lại).
 */
export async function bootstrapSession(deps: BootstrapDeps): Promise<BootstrapResult> {
  let me: Me | null;
  try {
    me = await deps.restoreSession();
  } catch (error) {
    return fallback(deps, error, null);
  }
  if (!me) return { status: 'signedOut' };
  try {
    const status = await deps.getOnboardingStatus();
    return { status: 'signedIn', me, onboardingEntry: onboardingEntry(status), stale: false };
  } catch (error) {
    return fallback(deps, error, me);
  }
}

async function fallback(deps: BootstrapDeps, error: unknown, me: Me | null): Promise<BootstrapResult> {
  if (isSessionRejected(error)) {
    await deps.clearTokens();
    await deps.clearSnapshot().catch(() => undefined);
    return { status: 'signedOut' };
  }
  const snapshot = await deps.loadSnapshot().catch(() => null);
  if (!snapshot || (me && snapshot.me.userId !== me.userId)) return { status: 'signedOut' };
  return { status: 'signedIn', me: me ?? snapshot.me, onboardingEntry: snapshot.onboardingEntry, stale: true };
}
