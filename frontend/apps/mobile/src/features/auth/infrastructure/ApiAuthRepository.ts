import type { PelaClient } from '@pela/api-contract';
import { call, unwrap } from '../../../core/infrastructure/http';
import type { AuthRepository } from '../application';

/** Auth qua backend /v1/auth/* (OpenAPI tag identity). */
export function createApiAuthRepository(client: PelaClient): AuthRepository {
  return {
    register: (body) => call(async () => unwrap(await client.POST('/auth/register', { body }))),
    async login(body) {
      const res = unwrap(await call(() => client.POST('/auth/login', { body })));
      return { accessToken: res.accessToken, refreshToken: res.refreshToken };
    },
    async logout(allDevices) {
      unwrap(await call(() => client.POST('/auth/logout', { body: { allDevices } })));
    },
    getMe: () => call(async () => unwrap(await client.GET('/me'))),
    async requestPasswordReset(email) {
      unwrap(await call(() => client.POST('/auth/password/forgot', { body: { email } })));
    },
    async verifyPasswordOtp(email, code) {
      const res = unwrap(await call(() => client.POST('/auth/password/verify-otp', { body: { email, code } })));
      return { resetToken: res.resetToken, expiresInSec: res.expiresIn };
    },
    async resetPassword(body) {
      unwrap(await call(() => client.POST('/auth/password/reset', { body })));
    },
  };
}
