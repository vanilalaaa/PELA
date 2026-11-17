import { describe, expect, it } from 'vitest';
import { avatarInitial, clampProgress, clampStars, formatBadgeCount, formatStatNumber } from './display';

describe('clampProgress', () => {
  it('kẹp về [0,1]', () => {
    expect(clampProgress(-0.2)).toBe(0);
    expect(clampProgress(0.5)).toBe(0.5);
    expect(clampProgress(1.4)).toBe(1);
    expect(clampProgress(Number.NaN)).toBe(0);
    expect(clampProgress(Infinity)).toBe(1);
  });
});

describe('formatBadgeCount', () => {
  it('hiện số, quá max thì max+', () => {
    expect(formatBadgeCount(0)).toBe('0');
    expect(formatBadgeCount(7)).toBe('7');
    expect(formatBadgeCount(99)).toBe('99');
    expect(formatBadgeCount(100)).toBe('99+');
    expect(formatBadgeCount(12, 9)).toBe('9+');
    expect(formatBadgeCount(-3)).toBe('0');
  });
});

describe('formatStatNumber', () => {
  it('dùng dấu chấm ngăn nghìn', () => {
    expect(formatStatNumber(12)).toBe('12');
    expect(formatStatNumber(1250)).toBe('1.250');
    expect(formatStatNumber(1234567)).toBe('1.234.567');
    expect(formatStatNumber(-5)).toBe('0');
  });
});

describe('avatarInitial', () => {
  it('lấy chữ đầu của từ cuối, viết hoa', () => {
    expect(avatarInitial('Nguyễn Thị Uyên')).toBe('U');
    expect(avatarInitial('  an ')).toBe('A');
    expect(avatarInitial('đức')).toBe('Đ');
    expect(avatarInitial('')).toBe('?');
  });
});

describe('clampStars', () => {
  it('làm tròn và kẹp về [0,max]', () => {
    expect(clampStars(3.6, 5)).toBe(4);
    expect(clampStars(8, 6)).toBe(6);
    expect(clampStars(-1, 5)).toBe(0);
    expect(clampStars(Number.NaN, 5)).toBe(0);
  });
});
