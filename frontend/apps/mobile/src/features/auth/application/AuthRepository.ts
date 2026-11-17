import type {
  AuthTokens,
  LoginInput,
  Me,
  PasswordResetTicket,
  RegisterInput,
  RegisterResult,
  ResetPasswordInput,
} from './types';

/** Cổng ra của tính năng auth (UC-01, UC-04). Lỗi ném ApiRequestError theo khuôn API. */
export interface AuthRepository {
  register(input: RegisterInput): Promise<RegisterResult>;
  login(input: LoginInput): Promise<AuthTokens>;
  /** Thu hồi phiên phía server; `allDevices` = thoát mọi thiết bị (Auth.Logout.MultiDevice). */
  logout(allDevices: boolean): Promise<void>;
  getMe(): Promise<Me>;
  requestPasswordReset(email: string): Promise<void>;
  verifyPasswordOtp(email: string, code: string): Promise<PasswordResetTicket>;
  resetPassword(input: ResetPasswordInput): Promise<void>;
}

/** Nơi lưu token phiên (hiện thực: secure-store ở core/infrastructure/http). */
export interface SessionTokenPort {
  get(): Promise<AuthTokens | null>;
  set(tokens: AuthTokens): Promise<void>;
  clear(): Promise<void>;
}
