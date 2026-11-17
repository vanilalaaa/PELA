import authFixture from '@pela/fixtures/mock/auth.json';
import { ApiRequestError } from '../../../core/infrastructure/http';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { AuthRepository, AuthTokens, Me } from '../application';

interface MockErrorAccount {
  status: number;
  /** Mã lỗi theo OpenAPI. */
  code: string;
  details?: Record<string, unknown>;
  /** Mặc định áp cho đăng nhập; 'register' = chỉ khi đăng ký. */
  on?: 'register';
}

interface AuthFixture {
  me: Me;
  tokens: AuthTokens;
  /** Mật khẩu sai để thử luồng lỗi INVALID_CREDENTIALS. */
  wrongPassword: string;
  otpCode: string;
  /** Email mẫu để thử từng lỗi đăng nhập/đăng ký (UC-01 E1–E4). */
  errorAccounts: Record<string, MockErrorAccount>;
}

const data = fixture<AuthFixture>(authFixture);
const RESET = { resetToken: 'mock-reset-token-0000000000000000000000000000', expiresInSec: 600 };

/** Mock khoá đăng nhập 15 phút (giá trị thật do server cấu hình: auth.lockout_minutes). */
const MOCK_LOCK_MINUTES = 15;

function errorFor(email: string, action: 'login' | 'register'): ApiRequestError | null {
  const account = data.errorAccounts[email.trim().toLowerCase()];
  if (!account || (account.on === 'register') !== (action === 'register')) return null;
  const details =
    account.code === 'ACCOUNT_LOCKED'
      ? { lockedUntil: new Date(Date.now() + MOCK_LOCK_MINUTES * 60_000).toISOString() }
      : account.details;
  return new ApiRequestError(account.status, account.code, 'Mock error', details);
}

/** Auth giả lập (dùng khi chưa có backend). Chế độ "Rỗng" không áp cho auth: coi như bình thường. */
export function createMockAuthRepository(): AuthRepository {
  return {
    register: (input) =>
      mockCall(() => {
        const error = errorFor(input.email, 'register');
        if (error) throw error;
        return { userId: data.me.userId, email: input.email.trim(), status: 'unverified' as const };
      }, { userId: data.me.userId, email: input.email, status: 'unverified' as const }),
    login: (input) =>
      mockCall(() => {
        const error = errorFor(input.email, 'login');
        if (error) throw error;
        if (input.password === data.wrongPassword) {
          throw new ApiRequestError(401, 'INVALID_CREDENTIALS', 'Invalid email or password');
        }
        return clone(data.tokens);
      }, clone(data.tokens)),
    logout: () => mockCall(() => undefined, undefined),
    getMe: () => mockCall(() => clone(data.me), clone(data.me)),
    requestPasswordReset: () => mockCall(() => undefined, undefined),
    verifyPasswordOtp: (_email, code) =>
      mockCall(() => {
        if (code !== data.otpCode) throw new ApiRequestError(422, 'INVALID_OTP', 'Invalid code');
        return { ...RESET };
      }, { ...RESET }),
    resetPassword: () => mockCall(() => undefined, undefined),
  };
}
