import type { AuthRepository, SessionTokenPort } from './AuthRepository';
import type { LoginInput, Me, RegisterInput, ResetPasswordInput } from './types';

export interface AuthUseCases {
  register(input: RegisterInput): ReturnType<AuthRepository['register']>;
  /**
   * Đăng nhập rồi lưu token; trả thông tin tài khoản. `remember` (mặc định true): có lưu phiên bền
   * trên thiết bị không (Auth.Login.Remember).
   */
  login(input: LoginInput, options?: { remember?: boolean }): Promise<Me>;
  /** Gọi /auth/logout rồi LUÔN xoá token, kể cả khi server lỗi hoặc mất mạng. */
  logout(allDevices?: boolean): Promise<void>;
  /** Khởi động app: có token thì lấy /me; không có thì null. Lỗi ném ra để caller về màn đăng nhập. */
  restoreSession(): Promise<Me | null>;
  getMe(): Promise<Me>;
  requestPasswordReset(email: string): Promise<void>;
  verifyPasswordOtp: AuthRepository['verifyPasswordOtp'];
  resetPassword(input: ResetPasswordInput): Promise<void>;
}

export function createAuthUseCases(repo: AuthRepository, tokens: SessionTokenPort): AuthUseCases {
  return {
    register: (input) => repo.register(input),
    async login(input, options) {
      const session = await repo.login(input);
      await tokens.set(session, { persist: options?.remember ?? true });
      return repo.getMe();
    },
    async logout(allDevices = false) {
      try {
        await repo.logout(allDevices);
      } finally {
        await tokens.clear();
      }
    },
    async restoreSession() {
      const current = await tokens.get();
      if (!current) return null;
      return repo.getMe();
    },
    getMe: () => repo.getMe(),
    requestPasswordReset: (email) => repo.requestPasswordReset(email),
    verifyPasswordOtp: (email, code) => repo.verifyPasswordOtp(email, code),
    resetPassword: (input) => repo.resetPassword(input),
  };
}
