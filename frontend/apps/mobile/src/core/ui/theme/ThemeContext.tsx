import React, { createContext, useContext } from 'react';
import { colorTokens, radius, shadows, spacing, textStyles, typography, type ColorTokens } from './tokens';

export interface Theme {
  colors: ColorTokens;
  spacing: typeof spacing;
  radius: typeof radius;
  typography: typeof typography;
  textStyles: typeof textStyles;
  shadows: typeof shadows;
}

/**
 * Chỉ 1 bảng màu (light) — Figma chưa thiết kế dark mode nên không có toggle/scheme ở đây.
 * Theme tĩnh, không cần state — giữ Context/Provider để component vẫn dùng useTheme() như
 * bình thường, và dễ mở rộng lại nếu sau này Figma có thêm dark mode.
 */
const theme: Theme = {
  colors: colorTokens,
  spacing,
  radius,
  typography,
  textStyles,
  shadows,
};

const ThemeContext = createContext<Theme>(theme);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>;
}

/** Lấy theme hiện tại (màu + spacing + radius + typography + textStyles + shadows). */
export function useTheme(): Theme {
  return useContext(ThemeContext);
}
