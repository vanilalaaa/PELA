import { useCallback, useEffect } from 'react';
import type { LoginInput } from '../../features/auth/application';
import { isOnboardingDone } from '../../features/onboarding/application/onboardingUseCases';
import { infrastructure, onSessionExpired } from '../container';
import { bootstrapSession } from './bootstrapSession';
import { useUseCases } from '../UseCasesContext';
import { useSession } from './sessionStore';

/**
 * Khởi động phiên một lần: có token thì lấy /me + trạng thái onboarding, không thì về signedOut.
 * Đồng thời nghe sự kiện "phiên hết hạn" (refresh thất bại) để đưa app về AuthStack.
 */
export function useSessionBootstrap() {
  const { auth, onboarding } = useUseCases();

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const result = await bootstrapSession({
          restoreSession: () => auth.restoreSession(),
          getOnboardingStatus: () => onboarding.getStatus(),
          clearTokens: () => infrastructure.tokenStore.clear(),
        });
        if (!active) return;
        if (result.status === 'signedIn') useSession.getState().signIn(result.me, result.onboardingDone);
        else useSession.getState().signOut();
      } catch {
        if (active) useSession.getState().signOut();
      }
    })();
    const unsubscribe = onSessionExpired(() => useSession.getState().signOut());
    return () => {
      active = false;
      unsubscribe();
    };
  }, [auth, onboarding]);
}

/** Hành động đăng nhập/đăng xuất dùng ở màn; lỗi (ApiRequestError) ném lại cho màn hiển thị. */
export function useSessionActions() {
  const { auth, onboarding } = useUseCases();

  const login = useCallback(
    async (input: LoginInput) => {
      const me = await auth.login(input);
      const status = await onboarding.getStatus();
      useSession.getState().signIn(me, isOnboardingDone(status));
    },
    [auth, onboarding],
  );

  const logout = useCallback(async () => {
    try {
      await auth.logout();
    } finally {
      useSession.getState().signOut();
    }
  }, [auth]);

  return { login, logout, completeOnboarding: useSession.getState().completeOnboarding };
}
