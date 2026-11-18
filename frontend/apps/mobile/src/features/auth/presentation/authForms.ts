/**
 * Luật form và ánh xạ lỗi cho màn Auth (UC-01, Auth.Register.Validate, BR-1). Hàm thuần, có test.
 * Thông điệp là key i18n dưới `auth.validation` / `auth.error`; màn tự dịch.
 * Mã lỗi theo docs/api/openapi.yaml (không tự đặt mã mới).
 */
import { z } from 'zod';
import { evaluatePassword } from '@pela/domain';

// Giới hạn theo OpenAPI RegisterRequest (fullName ≤ 100, email ≤ 150, password ≤ 72).
export const FULL_NAME_MAX = 100;
export const EMAIL_MAX = 150;
export const PASSWORD_MAX = 72;

const email = z
  .string()
  .trim()
  .min(1, 'required')
  .max(EMAIL_MAX, 'emailTooLong')
  .pipe(z.email('invalidEmail'));

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'required'),
});
export type LoginForm = z.infer<typeof loginSchema>;

const newPassword = z
  .string()
  .min(1, 'required')
  .max(PASSWORD_MAX, 'passwordTooLong')
  .refine((p) => evaluatePassword(p).valid, 'weakPassword');

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(1, 'required').max(FULL_NAME_MAX, 'nameTooLong'),
    email,
    password: newPassword,
    confirmPassword: z.string().min(1, 'required'),
  })
  .refine((d) => d.password === d.confirmPassword, { path: ['confirmPassword'], message: 'passwordMismatch' });
export type RegisterForm = z.infer<typeof registerSchema>;

/** 44b: email nhận mã (POST /auth/password/forgot). */
export const forgotSchema = z.object({ email });
export type ForgotForm = z.infer<typeof forgotSchema>;

/** 44d: mật khẩu mới theo luật như đăng ký (Auth.Recover.Reset, GAP-17). */
export const resetSchema = z
  .object({ password: newPassword, confirmPassword: z.string().min(1, 'required') })
  .refine((d) => d.password === d.confirmPassword, { path: ['confirmPassword'], message: 'passwordMismatch' });
export type ResetForm = z.infer<typeof resetSchema>;

/** Lỗi API dạng tối thiểu (presentation không import infrastructure). */
interface ApiErrorLike {
  status?: number;
  code?: string;
  details?: unknown;
  /** Header Retry-After (giây), có khi 429. */
  retryAfterSec?: number | null;
}

function asApiError(error: unknown): ApiErrorLike {
  return typeof error === 'object' && error !== null ? (error as ApiErrorLike) : {};
}

function detail(error: ApiErrorLike, key: string): string | null {
  const d = error.details;
  if (typeof d !== 'object' || d === null || Array.isArray(d)) return null;
  const v = (d as Record<string, unknown>)[key];
  return typeof v === 'string' ? v : null;
}

export type FieldErrors = Partial<Record<string, string>>;

/** VALIDATION_FAILED: details là danh sách {field, message} → lỗi chung "invalidValue" dưới từng ô. */
function validationFieldErrors(error: ApiErrorLike): FieldErrors {
  if (!Array.isArray(error.details)) return {};
  const out: FieldErrors = {};
  for (const item of error.details) {
    const field = (item as { field?: unknown } | null)?.field;
    if (typeof field === 'string') out[field] = 'invalidValue';
  }
  return out;
}

/** Lỗi chung cho mọi form auth (mạng, quá tải, dịch vụ xác thực, không rõ). */
export type CommonAlert =
  | { key: 'network' }
  | { key: 'tooManyRequests'; retryAfterSec: number | null }
  | { key: 'providerUnavailable' }
  | { key: 'unknown' };

function commonAlert(error: ApiErrorLike): CommonAlert {
  if (error.status === 0) return { key: 'network' };
  if (error.status === 429 || error.code === 'TOO_MANY_REQUESTS') {
    const sec = error.retryAfterSec;
    return { key: 'tooManyRequests', retryAfterSec: typeof sec === 'number' && sec > 0 ? sec : null };
  }
  if (error.status === 503 || error.code === 'AUTH_PROVIDER_UNAVAILABLE') return { key: 'providerUnavailable' };
  return { key: 'unknown' };
}

export type LoginAlert =
  | { key: 'invalidCredentials' }
  | { key: 'locked'; lockedUntil: string | null }
  | { key: 'unverified' }
  | { key: 'suspended'; reason: string | null; suspendedUntil: string | null }
  | { key: 'pendingDeletion'; deletionScheduledAt: string | null }
  | { key: 'invalidInput' }
  | CommonAlert;

/** UC-01 2.0.E1–E4, Auth.Login.Suspended/PendingDeletion (POST /auth/login). */
export function mapLoginError(raw: unknown): { alert: LoginAlert; fieldErrors: FieldErrors } {
  const e = asApiError(raw);
  switch (e.code) {
    case 'INVALID_CREDENTIALS':
      return { alert: { key: 'invalidCredentials' }, fieldErrors: { email: 'highlight', password: 'highlight' } };
    case 'ACCOUNT_LOCKED':
      return { alert: { key: 'locked', lockedUntil: detail(e, 'lockedUntil') }, fieldErrors: {} };
    case 'EMAIL_NOT_VERIFIED':
      return { alert: { key: 'unverified' }, fieldErrors: {} };
    case 'ACCOUNT_SUSPENDED':
      return {
        alert: { key: 'suspended', reason: detail(e, 'reason'), suspendedUntil: detail(e, 'suspendedUntil') },
        fieldErrors: {},
      };
    case 'ACCOUNT_PENDING_DELETION':
      return { alert: { key: 'pendingDeletion', deletionScheduledAt: detail(e, 'deletionScheduledAt') }, fieldErrors: {} };
    case 'VALIDATION_FAILED':
      return { alert: { key: 'invalidInput' }, fieldErrors: validationFieldErrors(e) };
    default:
      return { alert: commonAlert(e), fieldErrors: {} };
  }
}

export type RegisterAlert = { key: 'emailTaken' } | { key: 'invalidInput' } | CommonAlert;

/** UC-01 1.0.E1 (BR-1), POST /auth/register. */
export function mapRegisterError(raw: unknown): { alert: RegisterAlert; fieldErrors: FieldErrors } {
  const e = asApiError(raw);
  if (e.code === 'EMAIL_ALREADY_REGISTERED') return { alert: { key: 'emailTaken' }, fieldErrors: { email: 'emailTakenHint' } };
  if (e.code === 'VALIDATION_FAILED') return { alert: { key: 'invalidInput' }, fieldErrors: validationFieldErrors(e) };
  return { alert: commonAlert(e), fieldErrors: {} };
}

export type ForgotAlert = { key: 'invalidInput' } | CommonAlert;

/**
 * 44b / "Gửi lại mã" ở 44c (POST /auth/password/forgot). API luôn trả 202 với email hợp lệ dạng,
 * nên không có lỗi "email không tồn tại" (không tiết lộ tài khoản).
 */
export function mapForgotError(raw: unknown): { alert: ForgotAlert; fieldErrors: FieldErrors } {
  const e = asApiError(raw);
  if (e.code === 'VALIDATION_FAILED') return { alert: { key: 'invalidInput' }, fieldErrors: validationFieldErrors(e) };
  return { alert: commonAlert(e), fieldErrors: {} };
}

export type OtpAlert = { key: 'invalidOtp' } | CommonAlert;

/** 44c (POST /auth/password/verify-otp): INVALID_OTP = mã sai hoặc đã hết hạn (OpenAPI gộp một mã). */
export function mapOtpError(raw: unknown): OtpAlert {
  const e = asApiError(raw);
  if (e.code === 'INVALID_OTP' || e.code === 'VALIDATION_FAILED') return { key: 'invalidOtp' };
  return commonAlert(e);
}

export type ResetAlert = { key: 'resetExpired' } | { key: 'invalidInput' } | CommonAlert;

/** 44d (POST /auth/password/reset): token hỏng/hết hạn → banner hết hạn; mật khẩu bị từ chối → lỗi dưới ô. */
export function mapResetError(raw: unknown): { alert: ResetAlert | null; fieldErrors: FieldErrors } {
  const e = asApiError(raw);
  switch (e.code) {
    case 'INVALID_RESET_TOKEN':
      return { alert: { key: 'resetExpired' }, fieldErrors: {} };
    case 'WEAK_PASSWORD':
      return { alert: null, fieldErrors: { password: 'weakPassword' } };
    case 'SAME_PASSWORD':
      return { alert: null, fieldErrors: { password: 'samePassword' } };
    case 'VALIDATION_FAILED': {
      const fields = validationFieldErrors(e);
      // Server đặt tên ô theo body: newPassword/confirmPassword.
      const out: FieldErrors = {};
      if (fields.newPassword || fields.password) out.password = 'invalidValue';
      if (fields.confirmPassword) out.confirmPassword = 'invalidValue';
      return { alert: { key: 'invalidInput' }, fieldErrors: out };
    }
    default:
      return { alert: commonAlert(e), fieldErrors: {} };
  }
}

/** Mốc hết hạn phiên đặt lại (ISO) từ `expiresIn` server trả lúc xác thực mã. */
export function resetDeadline(nowMs: number, expiresInSec: number): string {
  return new Date(nowMs + Math.max(0, expiresInSec) * 1000).toISOString();
}

/** Đã quá hạn đặt lại chưa; mốc sai định dạng coi như hết hạn. */
export function isResetExpired(deadlineIso: string, nowMs: number): boolean {
  const at = Date.parse(deadlineIso);
  return Number.isNaN(at) || nowMs >= at;
}
