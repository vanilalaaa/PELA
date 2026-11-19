import { create } from 'zustand';
import type { Me } from '../../features/auth/application';
import type { OnboardingEntry } from '../../features/onboarding/application/types';

/** Trạng thái phiên dùng để chọn nhánh điều hướng (Auth / Onboarding / Main). */
export type AuthStatus = 'loading' | 'signedOut' | 'signedIn';

interface SessionState {
  status: AuthStatus;
  me: Me | null;
  onboardingDone: boolean;
  /** Màn mở đầu của OnboardingStack khi chưa xong (khôi phục theo trạng thái server). */
  onboardingEntry: OnboardingEntry;
  /** Phiên đang dùng ảnh chụp trên máy, chưa xác nhận lại với server (lỗi tạm thời khi mở app). */
  stale: boolean;
  signIn: (me: Me, onboardingEntry: OnboardingEntry, options?: { stale?: boolean }) => void;
  signOut: () => void;
  completeOnboarding: () => void;
  /** Ghi lại bước onboarding hiện tại (vd. khảo sát xong → PlacementIntro) để mở lại app đúng màn. */
  setOnboardingEntry: (entry: OnboardingEntry) => void;
}

export const useSession = create<SessionState>((set) => ({
  status: 'loading',
  me: null,
  onboardingDone: false,
  onboardingEntry: { screen: 'Survey' },
  stale: false,
  signIn: (me, onboardingEntry, options) =>
    set({ status: 'signedIn', me, onboardingEntry, onboardingDone: onboardingEntry === null, stale: options?.stale ?? false }),
  signOut: () => set({ status: 'signedOut', me: null, onboardingDone: false, onboardingEntry: { screen: 'Survey' }, stale: false }),
  completeOnboarding: () => set({ onboardingDone: true, onboardingEntry: null }),
  setOnboardingEntry: (entry) => set({ onboardingEntry: entry, onboardingDone: entry === null }),
}));
