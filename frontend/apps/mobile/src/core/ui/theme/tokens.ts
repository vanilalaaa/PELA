/**
 * Design tokens cho mobile app, khớp 1:1 với Figma Design System (file "Personalized English
 * Learning", page Design System 41:2). Nguồn: `get_variable_defs` trên Foundations / Colors
 * (448:42) và Foundations / Effects & Motion (449:255), cùng tài liệu trong các frame
 * Foundations, lấy ngày 2026-11-16. Tên biến Figma ghi trong comment để đối chiếu khi Figma đổi.
 *
 * Release 1.0 chỉ có giao diện sáng (Figma: "App chỉ có một giao diện sáng").
 *
 * Quy ước:
 * - Component và màn chỉ dùng token đã export (qua useTheme()), không hard-code hex hay số.
 * - `palette` là giá trị gốc (Figma Primitives, ẩn khỏi picker), chỉ dùng trong file này.
 * - Không dùng lại tên token cũ với giá trị mới: đổi giá trị thì đổi tên và sửa mọi chỗ dùng.
 */

// ---------------------------------------------------------------------------------------------
// Primitives (Figma collection Primitives) — KHÔNG export.
// ---------------------------------------------------------------------------------------------
const palette = {
  white: '#ffffff',
  blue: {
    25: '#f5faff',
    50: '#ebf5fe',
    100: '#d7ecfc',
    200: '#b0dafb',
    300: '#84c6f9',
    400: '#5cb6f7',
    500: '#38a7f6',
    600: '#1a8ceb',
    700: '#0072df',
    800: '#005bb3',
    900: '#06437f',
  },
  // Figma "Ink · neutral" (biến gray/*): xám ánh xanh, không dùng đen.
  ink: {
    50: '#f7fafd',
    100: '#f1f4f9',
    200: '#e4e9f2',
    300: '#c9d0de',
    400: '#98a2ba',
    500: '#6b7896',
    600: '#546180',
    700: '#3d4a66',
    800: '#2e3a55',
    900: '#1f2a44',
  },
  // amber/600, mint/600: thấy trong biến thể Pressed của PELA/Button (474:5706, 474:5826).
  amber: { 100: '#fff4c8', 300: '#ffe38a', 500: '#ffc53d', 600: '#ffb820', 700: '#a86a00' },
  coral: { 100: '#ffe1e6', 300: '#ffb3c0', 500: '#ff7a90', 700: '#c93b57' },
  mint: { 100: '#dcf5e3', 300: '#a8e6ba', 500: '#4cc97a', 600: '#3db86a', 700: '#1e8548' },
  sky: { 100: '#d7ecfc', 300: '#9fd0f8', 500: '#38a7f6', 700: '#0072df' },
  lilac: { 100: '#efe6ff', 500: '#9b7bff', 700: '#6445d1' },
  streak: { 100: '#ffe9d9', 500: '#ff8a3d', 700: '#d9560f' }, // 700 = streak-ink (StatPill 481:5729)
  tier: {
    1: { fill: '#e9a06c', ink: '#86461f' },
    2: { fill: '#dce5f0', ink: '#5a6c86' },
    3: { fill: '#ffd84a', ink: '#936400' },
    4: { fill: '#e3f4f6', ink: '#44788c' },
    5: { fill: '#9fe6ff', ink: '#155f97' },
    6: { fill: '#c7a8ff', ink: '#5b34a8' },
  },
} as const;

// ---------------------------------------------------------------------------------------------
// Semantic colors (Figma collection Color).
// ---------------------------------------------------------------------------------------------
export interface ColorTokens {
  bgCanvas: string;
  bgSurface: string;
  bgSurfaceAlt: string;
  bgDisabled: string;
  bgPrimary: string;
  bgPrimaryHover: string;
  bgPrimaryPress: string;
  bgPrimarySubtle: string;
  bgTrack: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  textDisabled: string;
  textOnPrimary: string;
  textLink: string;
  textBrand: string;
  iconDefault: string;
  borderDefault: string;
  borderStrong: string;
  borderFocus: string;
  borderError: string;
  scrim: string;
  starBg: string;
  starFg: string;
  starAccent: string;
  warnBg: string;
  warnFg: string;
  warnAccent: string;
  successBg: string;
  successFg: string;
  successAccent: string;
  infoBg: string;
  infoFg: string;
  infoAccent: string;
  streakBg: string;
  streakFg: string;
}

export const colorTokens: ColorTokens = {
  bgCanvas: palette.blue[25], // var(--color-bg-canvas)
  bgSurface: palette.white, // var(--color-bg-surface)
  bgSurfaceAlt: palette.ink[100], // var(--color-bg-surface-alt)
  bgDisabled: palette.ink[200], // var(--color-bg-disabled)
  bgPrimary: palette.blue[500], // var(--color-bg-primary)
  bgPrimaryHover: palette.blue[600], // var(--color-bg-primary-hover) (web)
  bgPrimaryPress: palette.blue[700], // var(--primary-press)
  bgPrimarySubtle: palette.blue[50], // var(--color-bg-primary-subtle)
  bgTrack: palette.ink[200], // var(--color-bg-track)
  textPrimary: palette.ink[900], // var(--color-text-primary)
  textSecondary: palette.ink[700], // var(--color-text-secondary)
  textTertiary: palette.ink[500], // var(--color-text-tertiary)
  textDisabled: palette.ink[400], // var(--color-text-disabled)
  textOnPrimary: palette.white, // var(--color-text-on-primary)
  textLink: palette.blue[700], // var(--text-link)
  textBrand: palette.blue[700], // var(--primary-ink) (text/brand)
  iconDefault: palette.ink[500], // var(--color-icon-default)
  borderDefault: palette.ink[200], // var(--color-border-default)
  borderStrong: palette.ink[300], // var(--color-border-strong)
  borderFocus: palette.blue[500], // var(--color-border-focus)
  borderError: palette.coral[500], // var(--color-border-error)
  scrim: 'rgba(31, 42, 68, 0.38)', // lớp phủ modal: ink-900 38% (Figma có thêm blur 4px; app không dùng blur)
  starBg: palette.amber[100], // var(--star-bg)
  starFg: palette.amber[700], // var(--star-fg)
  starAccent: palette.amber[500], // star/accent — suy ra (tông 500), không có trong get_variable_defs
  warnBg: palette.coral[100], // var(--warn-bg)
  warnFg: palette.coral[700], // var(--warn-fg)
  warnAccent: palette.coral[500], // warn/accent — suy ra (tông 500)
  successBg: palette.mint[100], // var(--success-bg)
  successFg: palette.mint[700], // var(--success-fg)
  successAccent: palette.mint[500], // success/accent — suy ra (tông 500)
  infoBg: palette.sky[100], // var(--info-bg)
  infoFg: palette.sky[700], // var(--info-fg)
  infoAccent: palette.blue[500], // info/accent — suy ra (tông 500)
  streakBg: palette.streak[100], // var(--streak-bg)
  streakFg: palette.streak[500], // var(--streak)
};

/**
 * Màu theo trạng thái của component, lấy từ biến thể trong Figma (PELA/Button 474:6006,
 * Button / Danger solid 575:11744, IconButton 475:5355, Input 570:11731, OtpCell 578:11762,
 * Checkbox/Radio/Switch 476:5500/5515/5532). Figma dùng thẳng primitive (blue-100, blue-200…)
 * nên gom ở đây thay vì để component chạm vào palette.
 */
export const componentColors = {
  button: {
    primary: { bg: palette.blue[500], bgPressed: palette.blue[700], fg: palette.white },
    secondary: { bg: palette.white, bgPressed: palette.blue[100], fg: palette.blue[700], border: palette.blue[200] },
    soft: { bg: palette.blue[50], bgPressed: palette.blue[200], fg: palette.blue[700] },
    ghost: { bg: 'transparent', bgPressed: palette.blue[100], fg: palette.blue[700] },
    star: { bg: palette.amber[500], bgPressed: palette.amber[600], fg: palette.ink[900] },
    success: { bg: palette.mint[500], bgPressed: palette.mint[600], fg: palette.white },
    danger: { bg: palette.coral[100], bgPressed: palette.coral[300], fg: palette.coral[700] },
    // Figma không vẽ Pressed cho Danger solid → giữ nền, chỉ thu nhỏ khi nhấn (GAP).
    dangerSolid: { bg: palette.coral[700], bgPressed: palette.coral[700], fg: palette.white },
    // Nút trong banner lỗi (03 Register Error: "Đăng nhập" nền warn-accent coral-500). Figma không vẽ Pressed → coral-700.
    warn: { bg: palette.coral[500], bgPressed: palette.coral[700], fg: palette.white },
    disabled: { bg: palette.ink[200], fg: palette.ink[400] },
  },
  iconButton: {
    soft: { bg: palette.blue[50], bgPressed: palette.blue[200], fg: palette.blue[700] },
    ghost: { bg: 'transparent', bgPressed: palette.ink[200], fg: palette.ink[700] },
    primary: { bg: palette.blue[500], bgPressed: palette.blue[700], fg: palette.white },
    white: { bg: palette.white, bgPressed: palette.blue[100], fg: palette.ink[700] },
    disabled: { bg: palette.ink[100], fg: palette.ink[400] },
  },
  field: {
    bg: palette.white,
    bgDisabled: palette.ink[100],
    icon: palette.ink[400],
    iconFocused: palette.blue[600],
    caret: palette.blue[600],
    optionSelectedBg: palette.blue[50],
  },
  otp: { borderFilled: palette.blue[300] },
  control: {
    on: palette.blue[500],
    offBorder: palette.ink[300],
    switchOffTrack: palette.ink[300],
    knob: palette.white,
  },
  strength: {
    empty: palette.ink[100],
    1: { bar: palette.coral[500], text: palette.coral[700] },
    2: { bar: palette.amber[500], text: palette.amber[700] },
    3: { bar: palette.mint[500], text: palette.mint[700] },
  },
  // Nền tint phẳng của Card (503:9474) và IconCircle (475:5626): Blue dùng blue-50, tone khác tông 100.
  tint: {
    white: palette.white,
    blue: palette.blue[50],
    sky: palette.sky[100],
    amber: palette.amber[100],
    coral: palette.coral[100],
    mint: palette.mint[100],
    lilac: palette.lilac[100],
    streak: palette.streak[100],
    neutral: palette.ink[100],
  },
  // Tag 478:5509: chọn = nền tint + viền tông 500 + chữ tông 700; chưa chọn = trắng, viền ink-200.
  tag: {
    blue: { bg: palette.blue[50], border: palette.blue[500], fg: palette.blue[700] },
    amber: { bg: palette.amber[100], border: palette.amber[500], fg: palette.amber[700] },
    mint: { bg: palette.mint[100], border: palette.mint[500], fg: palette.mint[700] },
    coral: { bg: palette.coral[100], border: palette.coral[500], fg: palette.coral[700] },
    unselected: { bg: palette.white, border: palette.ink[200], fg: palette.ink[700] },
  },
  // Chip 643:18143: Soft = tông 100 / chữ 700; Solid = tông 500 / chữ trắng (Amber chữ ink-900).
  chip: {
    soft: {
      neutral: { bg: palette.ink[100], fg: palette.ink[700] },
      sky: { bg: palette.sky[100], fg: palette.sky[700] },
      mint: { bg: palette.mint[100], fg: palette.mint[700] },
      amber: { bg: palette.amber[100], fg: palette.amber[700] },
      coral: { bg: palette.coral[100], fg: palette.coral[700] },
    },
    solid: {
      neutral: { bg: palette.ink[900], fg: palette.white },
      sky: { bg: palette.blue[500], fg: palette.white },
      mint: { bg: palette.mint[500], fg: palette.white },
      amber: { bg: palette.amber[500], fg: palette.ink[900] },
      coral: { bg: palette.coral[500], fg: palette.white },
    },
  },
  // Badge 503:9333; viền chấm trắng 2px.
  badge: {
    blue: { bg: palette.blue[500], fg: palette.white },
    coral: { bg: palette.coral[500], fg: palette.white },
    mint: { bg: palette.mint[500], fg: palette.white },
    amber: { bg: palette.amber[500], fg: palette.ink[900] },
    soft: { bg: palette.blue[100], fg: palette.blue[700] },
    neutral: { bg: palette.ink[100], fg: palette.ink[700] },
    ring: palette.white,
  },
  // Avatar 478:5590: ring trắng 3px + blue-500 (tổng 6px).
  avatarRing: { inner: palette.white, outer: palette.blue[500] },
  // ProgressBar 503:9449: track ink-100, vệt sáng trắng 45%.
  progress: { track: palette.ink[100], highlight: 'rgba(255, 255, 255, 0.45)' },
  // AnswerOption 481:5935.
  answer: {
    idle: { bg: palette.white, border: palette.ink[200], keyBg: palette.ink[100], keyFg: palette.ink[500], fg: palette.ink[900] },
    selected: { bg: palette.blue[50], border: palette.blue[500], keyBg: palette.blue[500], keyFg: palette.white, fg: palette.blue[700] },
    correct: { bg: palette.mint[100], border: palette.mint[500], keyBg: palette.mint[500], keyFg: palette.white, fg: palette.mint[700] },
    wrong: { bg: palette.coral[100], border: palette.coral[500], keyBg: palette.coral[500], keyFg: palette.white, fg: palette.coral[700] },
    disabled: { bg: palette.ink[50], border: palette.ink[200], keyBg: palette.ink[100], keyFg: palette.ink[400], fg: palette.ink[400] },
  },
  // SocialButton 643:13988.
  socialButton: { bg: palette.white, border: palette.blue[200], fg: palette.ink[900] },
  // ChatBubble 457:1684, tone Sky.
  chatBubble: { bg: palette.sky[100], speaker: palette.blue[700], fg: palette.blue[800] },
  // Tooltip 479:5715.
  tooltip: { bg: palette.ink[900], fg: palette.white },
  // Tabs 480:5609 và BottomNav 480:5870.
  tabs: { track: palette.ink[100], active: palette.white, fgActive: palette.blue[700], fg: palette.ink[500] },
  bottomNav: { bg: palette.white, border: palette.ink[200], pill: palette.blue[50], fgActive: palette.blue[700], fg: palette.ink[400], badge: palette.coral[500] },
} as const;

/** Cặp màu theo tone cho component có biến thể Tone (Card, Tag, Chip, IconCircle, Avatar, ProgressBar, Badge). */
export interface ToneColors {
  bg: string; // tông 100: nền pastel
  fg: string; // tông 700: chữ/icon trên nền pastel
  accent: string; // tông 500: điểm nhấn, thanh tiến độ
}

export const tones = {
  blue: { bg: palette.blue[100], fg: palette.blue[700], accent: palette.blue[500] },
  sky: { bg: palette.sky[100], fg: palette.sky[700], accent: palette.sky[500] },
  amber: { bg: palette.amber[100], fg: palette.amber[700], accent: palette.amber[500] },
  coral: { bg: palette.coral[100], fg: palette.coral[700], accent: palette.coral[500] },
  mint: { bg: palette.mint[100], fg: palette.mint[700], accent: palette.mint[500] },
  lilac: { bg: palette.lilac[100], fg: palette.lilac[700], accent: palette.lilac[500] },
  streak: { bg: palette.streak[100], fg: palette.streak[700], accent: palette.streak[500] },
  neutral: { bg: palette.ink[100], fg: palette.ink[700], accent: palette.ink[500] },
} as const satisfies Record<string, ToneColors>;

/** Huy hiệu cấp bậc 6 tier: Tập sự (Đồng) → … → Bậc thầy (Tinh Anh). Figma tier-N / tier-N-ink. */
export const rankTiers = palette.tier;

// ---------------------------------------------------------------------------------------------
// Spacing (Figma Foundations / Spacing & Radius: lưới 4pt).
// ---------------------------------------------------------------------------------------------
export const spacing = {
  '2xs': 2,
  xs: 4,
  sm: 8,
  '2sm': 12,
  md: 16,
  '2md': 20,
  lg: 24,
  xl: 32,
  '2xl': 40,
  '3xl': 48,
  '4xl': 64,
  '5xl': 80,
  screenGutter: 20, // spacing/screen-gutter: lề màn hình
  tapMin: 44, // spacing/tap-min: vùng chạm tối thiểu (UI-3)
  bottomNavH: 76, // spacing/bottom-nav-h
} as const;

// ---------------------------------------------------------------------------------------------
// Radius: thang + theo thành phần (Figma radius/*). Button, chip, tab, stat pill, thanh tiến
// độ dùng `full` (pill). Chat bubble bo 20, góc hướng về mascot bo 6.
// ---------------------------------------------------------------------------------------------
export const radii = {
  sm: 4,
  md: 8,
  lg: 12,
  xl: 16,
  '2xl': 24,
  '3xl': 32,
  full: 9999,
  checkbox: 8,
  key: 12,
  input: 16,
  answer: 20,
  bubble: 20,
  bubbleTail: 6, // radius/bubble-tail: góc chỉ về người nói (ChatBubble 457:1684)
  card: 24,
  dialog: 28,
  sheet: 28,
} as const;

// ---------------------------------------------------------------------------------------------
// Typography. Mỗi độ đậm là một file font riêng (assets/fonts, nhúng qua expo-font trong
// app.json); không đặt thêm fontWeight để Android không làm đậm giả.
// ---------------------------------------------------------------------------------------------
export const typography = {
  fontFamily: {
    nunitoRegular: 'Nunito-Regular',
    nunitoSemiBold: 'Nunito-SemiBold',
    nunitoBold: 'Nunito-Bold',
    nunitoExtraBold: 'Nunito-ExtraBold',
    balooBold: 'Baloo2-Bold',
    balooExtraBold: 'Baloo2-ExtraBold',
  },
} as const;

const font = typography.fontFamily;

/** Figma ghi letterSpacing theo % cỡ chữ; RN dùng px → px = % × fontSize. */
const ls = (percent: number, fontSize: number) => Math.round(percent * fontSize) / 100;

export interface TextStyleToken {
  fontFamily: string;
  fontSize: number;
  lineHeight: number;
  letterSpacing: number;
  textTransform?: 'uppercase';
}

/** Text style PELA/* trong Figma (Foundations / Typography). */
export const typeScale = {
  display: { fontFamily: font.balooExtraBold, fontSize: 34, lineHeight: 40, letterSpacing: ls(-1, 34) },
  h1: { fontFamily: font.balooExtraBold, fontSize: 28, lineHeight: 34, letterSpacing: ls(-1, 28) },
  h2: { fontFamily: font.balooBold, fontSize: 22, lineHeight: 28, letterSpacing: ls(-1, 22) },
  h3: { fontFamily: font.balooBold, fontSize: 18, lineHeight: 24, letterSpacing: ls(-1, 18) },
  numberLg: { fontFamily: font.balooExtraBold, fontSize: 40, lineHeight: 44, letterSpacing: 0 },
  buttonLg: { fontFamily: font.balooBold, fontSize: 18, lineHeight: 22, letterSpacing: ls(1, 18) },
  buttonMd: { fontFamily: font.balooBold, fontSize: 16, lineHeight: 20, letterSpacing: ls(1, 16) },
  buttonSm: { fontFamily: font.balooBold, fontSize: 14, lineHeight: 18, letterSpacing: ls(1, 14) },
  bodyLg: { fontFamily: font.nunitoSemiBold, fontSize: 17, lineHeight: 26, letterSpacing: 0 },
  body: { fontFamily: font.nunitoSemiBold, fontSize: 15, lineHeight: 22, letterSpacing: 0 },
  bodyStrong: { fontFamily: font.nunitoExtraBold, fontSize: 15, lineHeight: 22, letterSpacing: 0 },
  bodyReading: { fontFamily: font.nunitoRegular, fontSize: 15, lineHeight: 22, letterSpacing: 0 },
  bodyBold: { fontFamily: font.nunitoBold, fontSize: 15, lineHeight: 22, letterSpacing: 0 },
  caption: { fontFamily: font.nunitoSemiBold, fontSize: 13, lineHeight: 18, letterSpacing: 0 },
  captionBold: { fontFamily: font.nunitoBold, fontSize: 13, lineHeight: 18, letterSpacing: 0 },
  labelSpeaker: { fontFamily: font.balooExtraBold, fontSize: 12, lineHeight: 16, letterSpacing: 0 },
  // Input (570:11731) không gắn text style: nhãn Nunito 800 14/20, giá trị Nunito 600 16/22.
  inputLabel: { fontFamily: font.nunitoExtraBold, fontSize: 14, lineHeight: 20, letterSpacing: 0 },
  inputValue: { fontFamily: font.nunitoSemiBold, fontSize: 16, lineHeight: 22, letterSpacing: 0 },
  micro: { fontFamily: font.nunitoBold, fontSize: 11, lineHeight: 14, letterSpacing: 0 },
  // Style riêng của component (Figma không gắn text style):
  tab: { fontFamily: font.balooBold, fontSize: 15, lineHeight: 20, letterSpacing: 0.15 }, // Tabs 480:5609
  navLabel: { fontFamily: font.nunitoExtraBold, fontSize: 11, lineHeight: 14, letterSpacing: 0 }, // BottomNav
  // Figma dùng Nunito Black 12 cho Badge; app chưa nhúng Black → ExtraBold (GAP-19).
  badge: { fontFamily: font.nunitoExtraBold, fontSize: 12, lineHeight: 16, letterSpacing: 0 },
  statValue: { fontFamily: font.balooExtraBold, fontSize: 16, lineHeight: 20, letterSpacing: 0 }, // StatPill, phím đáp án
  progressLabel: { fontFamily: font.balooExtraBold, fontSize: 14, lineHeight: 18, letterSpacing: 0 },
  answerLabel: { fontFamily: font.nunitoExtraBold, fontSize: 16, lineHeight: 22, letterSpacing: 0 }, // AnswerOption
  eyebrow: {
    fontFamily: font.nunitoExtraBold,
    fontSize: 11,
    lineHeight: 14,
    letterSpacing: ls(6, 11),
    textTransform: 'uppercase',
  },
} as const satisfies Record<string, TextStyleToken>;

// ---------------------------------------------------------------------------------------------
// Elevation (Figma effect styles PELA/shadow-*). Bóng ánh xanh rgba(31,72,130). Opacity lấy
// từ alpha hex (vd. #1F48820F → 0x0F/255). `elevation` cho Android là suy ra theo độ nổi.
// ---------------------------------------------------------------------------------------------
const shadow = (color: string, alphaHex: number, y: number, blur: number, androidElevation: number) => ({
  shadowColor: color,
  shadowOpacity: Math.round((alphaHex / 255) * 1000) / 1000,
  shadowOffset: { width: 0, height: y },
  shadowRadius: blur,
  elevation: androidElevation,
});

export const elevation = {
  xs: shadow('#1F4882', 0x0f, 1, 2, 1), // tách nhẹ phần tử phẳng
  sm: shadow('#1F4882', 0x14, 2, 8, 2), // card trắng mặc định
  md: shadow('#1F4882', 0x1a, 6, 18, 4), // card nổi, dropdown, toast
  lg: shadow('#1F4882', 0x24, 14, 36, 8), // dialog, bottom sheet
  primary: shadow(palette.blue[500], 0x59, 6, 16, 5), // button primary (mất khi press/disabled)
  star: shadow(palette.amber[500], 0x59, 6, 16, 5), // button sao / phần thưởng
  success: shadow(palette.mint[500], 0x59, 6, 16, 5), // PELA/shadow-success (Button Success, thấy ở 474:5806)
} as const;

/**
 * Vòng sáng quanh ô nhập. RN không có box-shadow spread → component vẽ một viền ngoài dày `width`.
 * focus: PELA/ring-focus #38A7F6 α47 spread 4. error: Figma ghi "vòng coral 22%, chưa có token"
 * (Input 570:11731) → coral-500 α 0.22, cùng độ dày.
 */
export const rings = {
  focus: { color: 'rgba(56, 167, 246, 0.278)', width: 4 },
  error: { color: 'rgba(255, 122, 144, 0.22)', width: 4 },
} as const;

// ---------------------------------------------------------------------------------------------
// Motion (Figma Foundations / Effects & Motion).
// ---------------------------------------------------------------------------------------------
export const motion = {
  duration: { fast: 120, base: 200, slow: 360 },
  easing: {
    soft: [0.25, 0.8, 0.35, 1], // UI thông thường
    bounce: [0.34, 1.56, 0.64, 1], // phần thưởng, chấm radio, núm switch, mở huy hiệu
  },
  pressScale: { button: 0.96, iconButton: 0.92, card: 0.98 },
  // Mờ khi nhấn cho mục Tabs/BottomNav (Figma không có Pressed, GAP-22).
  pressOpacity: 0.6,
} as const;

export type Tones = typeof tones;
export type RankTiers = typeof rankTiers;
export type Spacing = typeof spacing;
export type Radii = typeof radii;
export type Typography = typeof typography;
export type TypeScale = typeof typeScale;
export type Elevation = typeof elevation;
export type Rings = typeof rings;
export type ComponentColors = typeof componentColors;
export type Motion = typeof motion;
