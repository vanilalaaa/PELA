import type { PasswordEvaluation } from '@pela/domain';

/**
 * Phần chữ dưới thanh độ mạnh (PasswordStrength), dạng key i18n dưới `ui.password`:
 * - chưa nhập (level 0) → chỉ yêu cầu;
 * - còn điều kiện chưa đạt → mức + nhắc yêu cầu;
 * - đủ 4 điều kiện nhưng mới Trung bình → mức + gợi ý tăng độ mạnh;
 * - Mạnh → chỉ mức.
 */
export function passwordCaptionKeys(result: Pick<PasswordEvaluation, 'level' | 'valid'>): string[] {
  if (result.level === 0) return ['requirement'];
  const level = `level${result.level}`;
  if (!result.valid) return [level, 'requirement'];
  if (result.level < 3) return [level, 'strongerHint'];
  return [level];
}
