import authFixture from '@pela/fixtures/mock/auth.json';
import { ApiRequestError } from '../../../core/infrastructure/http';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { AuthRepository, AuthTokens, Me } from '../application';

interface AuthFixture {
  me: Me;
  tokens: AuthTokens;
  /** Mật khẩu sai để thử luồng lỗi INVALID_CREDENTIALS. */
  wrongPassword: string;
  otpCode: string;
}

const data = fixture<AuthFixture>(authFixture);

/** Auth giả lập (dùng khi chưa có backend). Chế độ "Rỗng" không áp cho auth: coi như bình thường. */
export function createMockAuthRepository(): AuthRepository {
  return {
    register: (input) =>
      mockCall(() => ({ userId: data.me.userId, email: input.email, status: 'unverified' as const }), {
        userId: data.me.userId,
        email: input.email,
        status: 'unverified' as const,
      }),
    login: (input) =>
      mockCall(() => {
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
        return { resetToken: 'mock-reset-token-0000000000000000000000000000', expiresInSec: 600 };
      }, { resetToken: 'mock-reset-token-0000000000000000000000000000', expiresInSec: 600 }),
    resetPassword: () => mockCall(() => undefined, undefined),
  };
}
