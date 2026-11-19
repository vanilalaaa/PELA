import { describe, expect, it } from 'vitest';
import { formatDate, formatDateTime } from './dateFormat';

// Tạo mốc theo giờ máy để test không phụ thuộc múi giờ.
const iso = new Date(2026, 10, 7, 9, 5).toISOString();

describe('formatDateTime / formatDate', () => {
  it('vi: lúc HH:mm, dd/MM/yyyy', () => {
    expect(formatDateTime(iso, 'vi')).toBe('lúc 09:05, 07/11/2026');
    expect(formatDate(iso, 'vi-VN')).toBe('07/11/2026');
  });
  it('en: at HH:mm, MM/dd/yyyy', () => {
    expect(formatDateTime(iso, 'en')).toBe('at 09:05, 11/07/2026');
    expect(formatDate(iso, 'en-US')).toBe('11/07/2026');
  });
  it('null hoặc ISO hỏng → null', () => {
    expect(formatDateTime(null, 'vi')).toBeNull();
    expect(formatDate('not-a-date', 'en')).toBeNull();
  });
});
