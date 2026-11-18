import { describe, expect, it } from 'vitest';
import { maskEmail } from './maskEmail';

describe('maskEmail', () => {
  it('giữ 2 ký tự đầu và tên miền', () => {
    expect(maskEmail('linh@pela.vn')).toBe('li***@pela.vn');
    expect(maskEmail('  learner@example.com ')).toBe('le***@example.com');
  });
  it('tên ngắn giữ 1 ký tự', () => {
    expect(maskEmail('ab@pela.vn')).toBe('a***@pela.vn');
    expect(maskEmail('a@pela.vn')).toBe('a***@pela.vn');
  });
  it('không phải email thì trả nguyên', () => {
    expect(maskEmail('abc')).toBe('abc');
    expect(maskEmail('@pela.vn')).toBe('@pela.vn');
  });
});
