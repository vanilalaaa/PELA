import { describe, expect, it } from 'vitest';
import { OTP_LENGTH, otpCells, sanitizeOtp } from './otp';

describe('sanitizeOtp', () => {
  it('giữ nguyên mã 6 số', () => {
    expect(sanitizeOtp('482916')).toBe('482916');
  });

  it('bỏ khoảng trắng, gạch và chữ khi dán mã', () => {
    expect(sanitizeOtp(' 482-916 ')).toBe('482916');
    expect(sanitizeOtp('Mã của bạn: 482 916')).toBe('482916');
  });

  it('cắt về độ dài tối đa', () => {
    expect(sanitizeOtp('48291673')).toBe('482916');
    expect(sanitizeOtp('12345', 4)).toBe('1234');
  });

  it('chuyển số toàn chiều rộng thành số thường', () => {
    expect(sanitizeOtp('４８２９１６')).toBe('482916');
  });

  it('trả rỗng khi không có chữ số', () => {
    expect(sanitizeOtp('abc')).toBe('');
  });
});

describe('otpCells', () => {
  it('chia mã thành đủ số ô, ô thiếu là rỗng', () => {
    expect(otpCells('482')).toEqual(['4', '8', '2', '', '', '']);
    expect(otpCells('')).toHaveLength(OTP_LENGTH);
  });
});
