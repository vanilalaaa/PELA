import { create } from 'zustand';
import type { Me } from '../../features/auth/application';

/** Trạng thái phiên dùng để chọn nhánh điều hướng (Auth / Onboarding / Main). */
export type AuthStatus = 'loading' | 'signedOut' | 'signedIn';

interface SessionState {
  status: AuthStatus;
  me: Me | null;
  onboardingDone: boolean;
  signIn: (me: Me, onboardingDone: boolean) => void;
  signOut: () => void;
  completeOnboarding: () => void;
}

export const useSession = create<SessionState>((set) => ({
  status: 'loading',
  me: null,
  onboardingDone: false,
  signIn: (me, onboardingDone) => set({ status: 'signedIn', me, onboardingDone }),
  signOut: () => set({ status: 'signedOut', me: null, onboardingDone: false }),
  completeOnboarding: () => set({ onboardingDone: true }),
}));
