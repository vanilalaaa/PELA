import type { Me } from '../../features/auth/application';
import type { OnboardingStatus } from '../../features/onboarding/application/types';
import { isOnboardingDone } from '../../features/onboarding/application/onboardingUseCases';

export type BootstrapResult = { status: 'signedIn'; me: Me; onboardingDone: boolean } | { status: 'signedOut' };

export interface BootstrapDeps {
  /** auth.restoreSession: có token thì /me, không có thì null. */
  restoreSession: () => Promise<Me | null>;
  getOnboardingStatus: () => Promise<OnboardingStatus>;
  /** Xoá token đã lưu (secure-store). */
  clearTokens: () => Promise<void>;
}

function isUnauthorized(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { status?: unknown }).status === 401;
}

/**
 * Khôi phục phiên khi mở app. Có token và /me thành công → signedIn. Không có token → signedOut.
 * /me trả 401 (phiên hết hạn, refresh cũng thất bại) → xoá token, signedOut. Lỗi khác (mất mạng,
 * 5xx) → signedOut nhưng giữ token để lần mở sau thử lại.
 */
export async function bootstrapSession(deps: BootstrapDeps): Promise<BootstrapResult> {
  try {
    const me = await deps.restoreSession();
    if (!me) return { status: 'signedOut' };
    const status = await deps.getOnboardingStatus();
    return { status: 'signedIn', me, onboardingDone: isOnboardingDone(status) };
  } catch (error) {
    if (isUnauthorized(error)) await deps.clearTokens();
    return { status: 'signedOut' };
  }
}
