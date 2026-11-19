/**
 * Đánh giá mật khẩu khi đăng ký / đổi mật khẩu.
 *
 * Luật hợp lệ (Auth.Register.Validate, SRS v4.3): ít nhất 8 ký tự, có ≥1 chữ hoa, ≥1 chữ thường
 * và ≥1 chữ số. `valid` chỉ đúng khi đủ cả 4 luật.
 *
 * Mức độ mạnh (thanh 3 đoạn, Figma PasswordStrength 575:11948):
 * - 0: chưa nhập gì;
 * - 1 (Yếu): chưa đủ luật hợp lệ;
 * - 2 (Trung bình): đủ luật hợp lệ;
 * - 3 (Mạnh): đủ luật hợp lệ và (có ký tự đặc biệt hoặc dài ≥ 12).
 */
export interface PasswordRules {
  minLength: boolean;
  uppercase: boolean;
  lowercase: boolean;
  digit: boolean;
}

export type PasswordLevel = 0 | 1 | 2 | 3;

export interface PasswordEvaluation {
  rules: PasswordRules;
  level: PasswordLevel;
  valid: boolean;
}

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_STRONG_LENGTH = 12;

export function evaluatePassword(password: string): PasswordEvaluation {
  const length = Array.from(password).length;
  const rules: PasswordRules = {
    minLength: length >= PASSWORD_MIN_LENGTH,
    uppercase: /\p{Lu}/u.test(password),
    lowercase: /\p{Ll}/u.test(password),
    digit: /\p{Nd}/u.test(password),
  };
  const valid = rules.minLength && rules.uppercase && rules.lowercase && rules.digit;
  const hasSpecial = /[^\p{L}\p{N}\s]/u.test(password);

  let level: PasswordLevel;
  if (length === 0) level = 0;
  else if (!valid) level = 1;
  else if (hasSpecial || length >= PASSWORD_STRONG_LENGTH) level = 3;
  else level = 2;

  return { rules, level, valid };
}
