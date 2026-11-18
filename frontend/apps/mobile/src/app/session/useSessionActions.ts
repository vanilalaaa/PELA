import { useCallback, useEffect } from 'react';
import { onlineManager } from '@tanstack/react-query';
import type { LoginInput } from '../../features/auth/application';
import { onboardingEntry } from '../../features/onboarding/application/onboardingUseCases';
import { infrastructure, onSessionExpired, sessionSnapshots } from '../container';
import { bootstrapSession, isSessionRejected } from './bootstrapSession';
import { useUseCases } from '../UseCasesContext';
import { useSession } from './sessionStore';

/**
 * Khởi động phiên một lần: có token thì lấy /me + trạng thái onboarding, không thì về signedOut.
 * Lỗi tạm thời (mạng, timeout, 5xx) thì vào app bằng ảnh chụp phiên đã lưu (stale) và xác nhận lại khi
 * có mạng; chỉ về AuthStack khi server từ chối phiên. Đồng thời lưu ảnh chụp mỗi khi phiên đổi và nghe
 * sự kiện "phiên hết hạn" (refresh thất bại).
 */
export function useSessionBootstrap() {
  const { auth, onboarding } = useUseCases();

  useEffect(() => {
    let active = true;
    const deps = {
      restoreSession: () => auth.restoreSession(),
      getOnboardingStatus: () => onboarding.getStatus(),
      clearTokens: () => infrastructure.tokenStore.clear(),
      loadSnapshot: () => sessionSnapshots.load(),
      clearSnapshot: () => sessionSnapshots.clear(),
    };
    (async () => {
      try {
        const result = await bootstrapSession(deps);
        if (!active) return;
        if (result.status === 'signedIn') useSession.getState().signIn(result.me, result.onboardingEntry, { stale: result.stale });
        else useSession.getState().signOut();
      } catch {
        if (active) useSession.getState().signOut();
      }
    })();

    // Đang dùng ảnh chụp: có mạng lại thì gọi lại /me. Giữ màn onboarding đang ở (không giật màn).
    let verifying = false;
    const verify = async () => {
      if (verifying || !useSession.getState().stale || !onlineManager.isOnline()) return;
      verifying = true;
      try {
        const me = await auth.getMe();
        if (active) useSession.setState({ me, stale: false });
      } catch (error) {
        if (active && isSessionRejected(error)) {
          await infrastructure.tokenStore.clear();
          useSession.getState().signOut();
        }
      } finally {
        verifying = false;
      }
    };
    const unsubscribeOnline = onlineManager.subscribe((online) => {
      if (online) void verify();
    });

    // Lưu ảnh chụp khi tài khoản hoặc bước onboarding đổi; đăng xuất thì xoá.
    const unsubscribeStore = useSession.subscribe((state, prev) => {
      if (state.status === 'signedOut' && prev.status !== 'signedOut') void sessionSnapshots.clear().catch(() => undefined);
      if (state.status === 'signedIn' && state.me && (state.me !== prev.me || state.onboardingEntry !== prev.onboardingEntry)) {
        void sessionSnapshots.save({ me: state.me, onboardingEntry: state.onboardingEntry }).catch(() => undefined);
      }
      if (state.stale && !prev.stale) void verify();
    });

    const unsubscribeExpired = onSessionExpired(() => useSession.getState().signOut());
    return () => {
      active = false;
      unsubscribeOnline();
      unsubscribeStore();
      unsubscribeExpired();
    };
  }, [auth, onboarding]);
}

/** Hành động đăng nhập/đăng xuất dùng ở màn; lỗi (ApiRequestError) ném lại cho màn hiển thị. */
export function useSessionActions() {
  const { auth, onboarding } = useUseCases();

  const login = useCallback(
    async (input: LoginInput, remember = true) => {
      const me = await auth.login(input, { remember });
      const status = await onboarding.getStatus();
      useSession.getState().signIn(me, onboardingEntry(status));
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
