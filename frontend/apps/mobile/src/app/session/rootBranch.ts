import type { AuthStatus } from './sessionStore';

/** Nhánh điều hướng gốc. */
export type RootBranch = 'splash' | 'auth' | 'onboarding' | 'main';

/** Chọn nhánh theo phiên: khởi động → splash; chưa đăng nhập → auth; chưa xong onboarding → onboarding. */
export function selectRootBranch(status: AuthStatus, onboardingDone: boolean): RootBranch {
  if (status === 'loading') return 'splash';
  if (status === 'signedOut') return 'auth';
  return onboardingDone ? 'main' : 'onboarding';
}
