import { createContext, useContext } from 'react';
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
 * Release 1.0 chỉ có giao diện sáng; team không làm dark mode. Theme tĩnh, không cần state —
 * vẫn đi qua Context để component dùng useTheme() thống nhất.
 */
export const theme: Theme = {
  colors: colorTokens,
  spacing,
  radius,
  typography,
  textStyles,
  shadows,
};

export const ThemeContext = createContext<Theme>(theme);

/** Lấy theme hiện tại (màu + spacing + radius + typography + textStyles + shadows). */
export function useTheme(): Theme {
  return useContext(ThemeContext);
}
