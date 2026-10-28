/**
 * Design tokens cho mobile app.
 *
 * Lấy THẬT từ Figma Variables (file "Personalized English Learning App", node 42:13) qua
 * Figma MCP (`get_variable_defs`), mẫu trên 8 màn: Welcome, Register Error, Lesson Question ·
 * Correct, Daily Challenge · Wrong, Roadmap, 32 Badges, Overlay · Đăng xuất, Practice Hub —
 * ngày 2026-10-28. Tên biến gốc được ghi lại trong comment mỗi dòng để dễ đối chiếu lại khi
 * Figma đổi.
 *
 * CHỈ có 1 bảng màu (light) — Figma chưa thiết kế dark mode nên không làm dark mode ở đây.
 *
 * Quy ước: mọi màu sắc trong component PHẢI lấy từ đây (qua useTheme()), không hardcode hex.
 */

export interface ColorTokens {
  background: string;
  surface: string;
  surfaceVariant: string;
  overlay: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  textDisabled: string;
  textOnPrimary: string;
  iconDefault: string;
  primary: string;
  primaryPressed: string;
  primaryDisabled: string;
  secondary: string;
  secondaryPressed: string;
  border: string;
  borderFocused: string;
  success: string;
  warning: string;
  danger: string;
  disabledBackground: string;
  disabledText: string;
  starFilled: string;
  starEmpty: string;
}

// Giá trị thật từ Figma Variables (collection Color, mode duy nhất — Figma không có dark mode).
export const colorTokens: ColorTokens = {
  background: '#f5faff', // var(--color-bg-canvas)
  surface: '#ffffff', // var(--color-bg-surface)
  surfaceVariant: '#f1f4f9', // var(--color-bg-surface-alt) = gray/100
  overlay: 'rgba(31, 42, 68, 0.45)', // KHÔNG thấy biến overlay riêng trong Figma — suy ra từ gray/900
  textPrimary: '#1f2a44', // var(--color-text-primary) = gray/900
  textSecondary: '#3d4a66', // var(--color-text-secondary)
  textTertiary: '#6b7896', // var(--color-text-tertiary)
  textDisabled: '#98a2ba', // var(--color-text-disabled) = gray/400
  textOnPrimary: '#ffffff', // var(--color-text-on-primary)
  iconDefault: '#6b7896', // var(--color-icon-default)
  primary: '#38a7f6', // var(--color-bg-primary) = blue/500
  primaryPressed: '#0072df', // var(--primary-press) = blue/700
  primaryDisabled: '#b0dafb', // var(--blue-200) — chưa thấy biến "primary disabled" riêng, dùng tint nhạt của primary
  secondary: '#ff8a3d', // var(--streak) — KHÔNG có "secondary" brand riêng trong Figma; dùng màu streak (điểm nhấn gamification rõ nhất trong design)
  secondaryPressed: '#d9560f', // var(--streak-ink)
  border: '#e4e9f2', // var(--color-border-default) = gray/200
  borderFocused: '#0072df', // var(--primary-ink)
  success: '#1e8548', // var(--success-fg) = mint/700
  warning: '#ffc53d', // var(--star-accent) = amber/500 — Figma không có "warning" riêng, dùng amber (cũng là màu star)
  danger: '#c93b57', // var(--color-text-error) = coral/700
  disabledBackground: '#e4e9f2', // var(--color-bg-disabled)
  disabledText: '#98a2ba', // var(--color-text-disabled)
  starFilled: '#ffc53d', // var(--star-accent) = amber/500
  starEmpty: '#e4e9f2', // gray/200 (không có biến "star rỗng" riêng, dùng border-default cho nhất quán với các track rỗng khác)
};

/**
 * Figma không có scale spacing dạng biến riêng (không xuất hiện trong get_variable_defs ở các
 * màn đã lấy mẫu) — giữ scale chuẩn 4px đang dùng, có thể chỉnh lại nếu sau này thấy biến
 * spacing/* xuất hiện ở màn khác.
 */
export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
} as const;

// Giá trị thật từ Figma Variables (radius/*, var(--radius-full)).
export const radius = {
  sm: 12, // radius/key (phím bàn phím ảo, chip nhỏ)
  md: 16, // radius/input (input, button)
  lg: 24, // radius/card
  full: 9999, // var(--radius-full) (avatar tròn, pill)
  answer: 20, // radius/answer (đáp án MCQ) — trùng giá trị radius/bubble
  sheet: 28, // radius/sheet (bottom sheet)
} as const;

/**
 * Type scale — size/lineHeight lấy THẬT từ Figma (pela/font-size/*, pela/line-height/*).
 * 2 font family thật: "Baloo 2" (heading/button, Bold–ExtraBold) và "Nunito" (body/caption,
 * SemiBold–ExtraBold). CHƯA bundle file font thật (.ttf) nên RN sẽ fallback về font hệ thống
 * cho tới khi thêm asset + load qua expo-font — xem TODO ở App.tsx.
 */
export const typography = {
  fontFamily: {
    regular: 'Nunito',
    medium: 'Nunito',
    bold: 'Baloo 2',
  },
  // Scale chung (map gần nhất với scale thật: micro/caption/body/h3/h2/h1).
  size: {
    xs: 11, // pela/font-size/micro
    sm: 13, // pela/font-size/caption
    md: 15, // pela/font-size/body
    lg: 18, // pela/font-size/h3
    xl: 22, // pela/font-size/h2
    xxl: 28, // pela/font-size/h1
  },
  lineHeight: {
    xs: 14,
    sm: 18,
    md: 22,
    lg: 24,
    xl: 28,
    xxl: 34,
  },
  weight: {
    regular: '400' as const,
    medium: '500' as const,
    bold: '700' as const,
  },
};

/**
 * Named text style thật từ Figma (PELA/display, PELA/h1, ...) — dùng khi cần khớp chính xác
 * với 1 text style cụ thể trong design, thay vì ghép tay size/lineHeight/weight ở trên.
 */
export const textStyles = {
  display: { fontFamily: 'Baloo 2', fontWeight: '800' as const, fontSize: 34, lineHeight: 40, letterSpacing: -1 },
  h1: { fontFamily: 'Baloo 2', fontWeight: '800' as const, fontSize: 28, lineHeight: 34, letterSpacing: -1 },
  h2: { fontFamily: 'Baloo 2', fontWeight: '700' as const, fontSize: 22, lineHeight: 28, letterSpacing: -1 },
  h3: { fontFamily: 'Baloo 2', fontWeight: '700' as const, fontSize: 18, lineHeight: 24, letterSpacing: -1 },
  eyebrow: { fontFamily: 'Nunito', fontWeight: '800' as const, fontSize: 11, lineHeight: 14, letterSpacing: 6 },
  body: { fontFamily: 'Nunito', fontWeight: '600' as const, fontSize: 15, lineHeight: 22, letterSpacing: 0 },
  bodyStrong: { fontFamily: 'Nunito', fontWeight: '800' as const, fontSize: 15, lineHeight: 22, letterSpacing: 0 },
  bodyBold: { fontFamily: 'Nunito', fontWeight: '700' as const, fontSize: 15, lineHeight: 22, letterSpacing: 0 },
  bodyLg: { fontFamily: 'Nunito', fontWeight: '600' as const, fontSize: 17, lineHeight: 26, letterSpacing: 0 },
  caption: { fontFamily: 'Nunito', fontWeight: '600' as const, fontSize: 13, lineHeight: 18, letterSpacing: 0 },
  captionBold: { fontFamily: 'Nunito', fontWeight: '700' as const, fontSize: 13, lineHeight: 18, letterSpacing: 0 },
  micro: { fontFamily: 'Nunito', fontWeight: '700' as const, fontSize: 11, lineHeight: 14, letterSpacing: 0 },
  buttonSm: { fontFamily: 'Baloo 2', fontWeight: '700' as const, fontSize: 14, lineHeight: 18, letterSpacing: 1 },
  buttonMd: { fontFamily: 'Baloo 2', fontWeight: '700' as const, fontSize: 16, lineHeight: 20, letterSpacing: 1 },
  buttonLg: { fontFamily: 'Baloo 2', fontWeight: '700' as const, fontSize: 18, lineHeight: 22, letterSpacing: 1 },
} as const;

/**
 * Effect style (DROP_SHADOW) thật từ Figma (PELA/shadow-*). Giá trị alpha giữ nguyên từ hex gốc
 * (vd. #38A7F659 -> opacity 0x59/255). Dùng qua shadow* + elevation (Android cần elevation riêng,
 * Figma không có khái niệm này nên elevation là suy ra hợp lý theo độ "nổi" của shadow).
 */
export const shadows = {
  xs: { shadowColor: '#1F4882', shadowOpacity: 0.06, shadowOffset: { width: 0, height: 1 }, shadowRadius: 2, elevation: 1 },
  sm: { shadowColor: '#1F4882', shadowOpacity: 0.08, shadowOffset: { width: 0, height: 2 }, shadowRadius: 8, elevation: 2 },
  lg: { shadowColor: '#1F4882', shadowOpacity: 0.14, shadowOffset: { width: 0, height: 14 }, shadowRadius: 36, elevation: 8 },
  primary: { shadowColor: '#38A7F6', shadowOpacity: 0.35, shadowOffset: { width: 0, height: 6 }, shadowRadius: 16, elevation: 5 },
  success: { shadowColor: '#4CC97A', shadowOpacity: 0.35, shadowOffset: { width: 0, height: 6 }, shadowRadius: 16, elevation: 5 },
} as const;

export type Spacing = typeof spacing;
export type Radius = typeof radius;
export type Typography = typeof typography;
export type TextStyles = typeof textStyles;
export type Shadows = typeof shadows;
