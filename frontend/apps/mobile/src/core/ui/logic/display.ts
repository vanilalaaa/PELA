/** Hàm thuần cho component hiển thị (không import React). */

/** Kẹp tỉ lệ tiến độ về [0, 1]; NaN coi là 0. */
export function clampProgress(value: number): number {
  if (!Number.isFinite(value)) return value === Infinity ? 1 : 0;
  return Math.min(1, Math.max(0, value));
}

/** Số trên Badge: quá `max` thì hiện "max+" (vd. 99+). Số âm coi là 0. */
export function formatBadgeCount(count: number, max = 99): string {
  const n = Math.max(0, Math.floor(count));
  return n > max ? `${max}+` : String(n);
}

/** Số trên StatPill dùng dấu chấm ngăn nghìn (Figma: 1.250). */
export function formatStatNumber(value: number): string {
  const n = Math.max(0, Math.floor(value));
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** Chữ cái đầu cho Avatar: 1 chữ của từ cuối (tên tiếng Việt đặt tên riêng ở cuối), viết hoa. */
export function avatarInitial(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  const last = words[words.length - 1];
  if (!last) return '?';
  const first = Array.from(last)[0] ?? '?';
  return first.toLocaleUpperCase('vi');
}

/** Số sao kẹp về [0, max] và làm tròn (StarRating chỉ hiển thị giá trị server trả về). */
export function clampStars(value: number, max: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(max, Math.max(0, Math.round(value)));
}
