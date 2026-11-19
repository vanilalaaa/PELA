import type { Me } from '../../features/auth/application';
import type { OnboardingEntry } from '../../features/onboarding/application/types';

/**
 * Ảnh chụp phiên lưu trên thiết bị: tài khoản và màn onboarding đang ở. Dùng khi mở app mà không xác
 * nhận được phiên với server vì lỗi tạm thời (mất mạng, timeout, 5xx).
 */
export interface SessionSnapshot {
  me: Me;
  onboardingEntry: OnboardingEntry;
}

export interface SessionSnapshotStore {
  load(): Promise<SessionSnapshot | null>;
  save(snapshot: SessionSnapshot): Promise<void>;
  clear(): Promise<void>;
}

/** Đọc ảnh chụp đã lưu; sai khuôn thì null. */
export function parseSessionSnapshot(raw: unknown): SessionSnapshot | null {
  if (typeof raw !== 'object' || raw === null) return null;
  const r = raw as Partial<SessionSnapshot>;
  const me = r.me as Partial<Me> | undefined;
  if (!me || typeof me.userId !== 'string') return null;
  const entry = r.onboardingEntry;
  const validEntry =
    entry === null ||
    (typeof entry === 'object' &&
      entry !== undefined &&
      (entry.screen === 'Survey' || (entry.screen === 'PlacementIntro' && typeof entry.orientedLevel === 'string')));
  if (!validEntry) return null;
  return { me: r.me as Me, onboardingEntry: entry as OnboardingEntry };
}
