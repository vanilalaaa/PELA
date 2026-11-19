import { ApiRequestError } from './ApiRequestError';
import type { SessionTokens, TokenStore } from './tokenStore';

export interface RefreshCoordinatorDeps {
  tokenStore: TokenStore;
  /** Gọi POST /auth/refresh; ném ApiRequestError khi lỗi. */
  requestRefresh: (refreshToken: string) => Promise<SessionTokens>;
  /** Phiên hết hạn hẳn (refresh bị từ chối): đưa app về trạng thái đã đăng xuất. */
  onSessionExpired: () => void;
}

export interface RefreshCoordinator {
  /**
   * Làm mới phiên một lần duy nhất (single-flight): các lời gọi đồng thời chờ chung một promise.
   * Trả token mới, hoặc `null` nếu không làm mới được.
   */
  refresh(): Promise<SessionTokens | null>;
}

/** Refresh bị server từ chối (token hỏng/hết hạn hoặc tài khoản bị chặn) → phải đăng xuất. */
function isRejected(error: unknown): boolean {
  return error instanceof ApiRequestError && (error.status === 401 || error.status === 403);
}

export function createRefreshCoordinator({ tokenStore, requestRefresh, onSessionExpired }: RefreshCoordinatorDeps): RefreshCoordinator {
  let inFlight: Promise<SessionTokens | null> | null = null;

  const run = async (): Promise<SessionTokens | null> => {
    const current = await tokenStore.get();
    if (!current) {
      onSessionExpired();
      return null;
    }
    try {
      const next = await requestRefresh(current.refreshToken);
      await tokenStore.set(next);
      return next;
    } catch (error) {
      if (isRejected(error)) {
        await tokenStore.clear();
        onSessionExpired();
      }
      // Lỗi mạng/5xx: giữ token, để request gốc trả 401 về cho caller; lần sau sẽ thử lại.
      return null;
    }
  };

  return {
    refresh() {
      if (!inFlight) {
        inFlight = run().finally(() => {
          inFlight = null;
        });
      }
      return inFlight;
    },
  };
}
