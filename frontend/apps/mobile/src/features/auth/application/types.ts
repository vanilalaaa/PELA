import type { Schemas } from '@pela/api-contract';

/** Tài khoản đang đăng nhập (GET /me; SRS 4.1.1 User). */
export type Me = Schemas['Me'];
export type RegisterInput = Schemas['RegisterRequest'];
export type RegisterResult = Schemas['RegisterResponse'];
export type LoginInput = Schemas['LoginRequest'];

/** Token của phiên (TokenResponse, bỏ trường chỉ dùng nội bộ). */
export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

/** Bước 2 của quên mật khẩu (POST /auth/password/verify-otp). */
export interface PasswordResetTicket {
  resetToken: string;
  expiresInSec: number;
}

export interface ResetPasswordInput {
  resetToken: string;
  newPassword: string;
  confirmPassword: string;
}
