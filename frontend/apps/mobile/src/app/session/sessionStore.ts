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
  signIn: (me: Me, onboardingEntry: OnboardingEntry) => void;
  signOut: () => void;
  completeOnboarding: () => void;
}

export const useSession = create<SessionState>((set) => ({
  status: 'loading',
  me: null,
  onboardingDone: false,
  onboardingEntry: { screen: 'Survey' },
  signIn: (me, onboardingEntry) => set({ status: 'signedIn', me, onboardingEntry, onboardingDone: onboardingEntry === null }),
  signOut: () => set({ status: 'signedOut', me: null, onboardingDone: false, onboardingEntry: { screen: 'Survey' } }),
  completeOnboarding: () => set({ onboardingDone: true, onboardingEntry: null }),
}));
