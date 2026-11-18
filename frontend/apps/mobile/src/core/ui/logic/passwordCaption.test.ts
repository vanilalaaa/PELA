import { describe, expect, it } from 'vitest';
import { evaluatePassword } from '@pela/domain';
import { passwordCaptionKeys } from './passwordCaption';

const keys = (p: string) => passwordCaptionKeys(evaluatePassword(p));

describe('passwordCaptionKeys', () => {
  it('chưa nhập → chỉ yêu cầu', () => {
    expect(keys('')).toEqual(['requirement']);
  });
  it('còn điều kiện chưa đạt → mức + nhắc yêu cầu', () => {
    const r = keys('abcdefgh');
    expect(r[0]).toMatch(/^level[12]$/);
    expect(r[1]).toBe('requirement');
  });
  it('đủ 4 điều kiện nhưng Trung bình → gợi ý tăng độ mạnh, không nhắc điều kiện', () => {
    expect(evaluatePassword('Abcdef12')).toMatchObject({ valid: true, level: 2 });
    expect(keys('Abcdef12')).toEqual(['level2', 'strongerHint']);
  });
  it('Mạnh → chỉ mức', () => {
    expect(keys('Abcdef12!xyz')).toEqual(['level3']);
  });
});
