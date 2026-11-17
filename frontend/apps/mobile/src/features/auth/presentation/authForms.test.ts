import { describe, expect, it } from 'vitest';
import { loginSchema, mapLoginError, mapRegisterError, registerSchema } from './authForms';

const VALID = { fullName: 'Trần Thuỳ Linh', email: 'linh@pela.vn', password: 'Abcdef12', confirmPassword: 'Abcdef12' };
/** Lỗi đầu tiên của mỗi ô (giống zodResolver: mỗi ô hiện một lỗi). */
const issues = (r: { success: boolean; error?: { issues: { path: PropertyKey[]; message: string }[] } }) => {
  const out: Record<string, string> = {};
  for (const i of r.error?.issues ?? []) out[String(i.path[0])] ??= i.message;
  return out;
};

describe('registerSchema (Auth.Register.Validate)', () => {
  it('hợp lệ', () => {
    expect(registerSchema.safeParse(VALID).success).toBe(true);
  });
  it('mật khẩu yếu (thiếu hoa/thường/số hoặc < 8) → weakPassword', () => {
    for (const p of ['abcdefg1', 'ABCDEFG1', 'Abcdefgh', 'Ab1']) {
      expect(issues(registerSchema.safeParse({ ...VALID, password: p, confirmPassword: p })).password).toBe('weakPassword');
    }
  });
  it('xác nhận không khớp → passwordMismatch ở confirmPassword', () => {
    expect(issues(registerSchema.safeParse({ ...VALID, confirmPassword: 'Abcdef13' })).confirmPassword).toBe('passwordMismatch');
  });
  it('trống, sai dạng, quá dài', () => {
    const r = issues(registerSchema.safeParse({ fullName: ' ', email: 'abc', password: '', confirmPassword: '' }));
    expect(r).toMatchObject({ fullName: 'required', email: 'invalidEmail', password: 'required', confirmPassword: 'required' });
    expect(issues(registerSchema.safeParse({ ...VALID, fullName: 'a'.repeat(101) })).fullName).toBe('nameTooLong');
    expect(issues(registerSchema.safeParse({ ...VALID, email: `${'a'.repeat(146)}@b.vn` })).email).toBe('emailTooLong');
    const long = `Aa1${'a'.repeat(70)}`;
    expect(issues(registerSchema.safeParse({ ...VALID, password: long, confirmPassword: long })).password).toBe('passwordTooLong');
  });
});

describe('loginSchema', () => {
  it('chỉ cần email đúng dạng và mật khẩu không trống (không kiểm độ mạnh)', () => {
    expect(loginSchema.safeParse({ email: ' a@b.vn ', password: 'x' }).success).toBe(true);
    expect(issues(loginSchema.safeParse({ email: 'a@', password: '' }))).toEqual({ email: 'invalidEmail', password: 'required' });
  });
});

describe('mapLoginError (UC-01 2.0.E1–E4, mã theo OpenAPI)', () => {
  it('sai thông tin → tô đỏ cả 2 ô', () => {
    expect(mapLoginError({ status: 401, code: 'INVALID_CREDENTIALS' })).toEqual({
      alert: { key: 'invalidCredentials' },
      fieldErrors: { email: 'highlight', password: 'highlight' },
    });
  });
  it('bị khoá kèm giờ mở', () => {
    expect(mapLoginError({ status: 423, code: 'ACCOUNT_LOCKED', details: { lockedUntil: '2026-11-17T15:00:00Z' } }).alert).toEqual({
      key: 'locked',
      lockedUntil: '2026-11-17T15:00:00Z',
    });
  });
  it('chưa xác thực email', () => {
    expect(mapLoginError({ status: 403, code: 'EMAIL_NOT_VERIFIED' }).alert).toEqual({ key: 'unverified' });
  });
  it('đình chỉ: lý do + ngày hết; null = vĩnh viễn', () => {
    expect(mapLoginError({ status: 403, code: 'ACCOUNT_SUSPENDED', details: { reason: 'Spam', suspendedUntil: null } }).alert).toEqual({
      key: 'suspended',
      reason: 'Spam',
      suspendedUntil: null,
    });
  });
  it('chờ xoá: ngày xoá vĩnh viễn', () => {
    expect(
      mapLoginError({ status: 403, code: 'ACCOUNT_PENDING_DELETION', details: { deletionScheduledAt: '2026-12-01T00:00:00Z' } }).alert,
    ).toEqual({ key: 'pendingDeletion', deletionScheduledAt: '2026-12-01T00:00:00Z' });
  });
  it('VALIDATION_FAILED → lỗi theo từng ô', () => {
    expect(mapLoginError({ status: 422, code: 'VALIDATION_FAILED', details: [{ field: 'email', message: 'x' }] })).toEqual({
      alert: { key: 'invalidInput' },
      fieldErrors: { email: 'invalidValue' },
    });
  });
  it('mạng / 429 / 503 / không rõ', () => {
    expect(mapLoginError({ status: 0, code: 'NETWORK_ERROR' }).alert.key).toBe('network');
    expect(mapLoginError({ status: 429, code: 'TOO_MANY_REQUESTS' }).alert.key).toBe('tooManyRequests');
    expect(mapLoginError({ status: 503, code: 'AUTH_PROVIDER_UNAVAILABLE' }).alert.key).toBe('providerUnavailable');
    expect(mapLoginError(new Error('x')).alert.key).toBe('unknown');
  });
});

describe('mapRegisterError (1.0.E1, BR-1)', () => {
  it('email đã đăng ký → banner + gợi ý dưới ô Email', () => {
    expect(mapRegisterError({ status: 409, code: 'EMAIL_ALREADY_REGISTERED' })).toEqual({
      alert: { key: 'emailTaken' },
      fieldErrors: { email: 'emailTakenHint' },
    });
  });
  it('lỗi khác dùng lỗi chung', () => {
    expect(mapRegisterError({ status: 0 }).alert.key).toBe('network');
  });
});
