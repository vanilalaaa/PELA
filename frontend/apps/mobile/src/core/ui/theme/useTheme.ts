import { createContext, useContext } from 'react';
import {
  colorTokens,
  elevation,
  focusRing,
  motion,
  radii,
  rankTiers,
  spacing,
  tones,
  typeScale,
  typography,
  type ColorTokens,
} from './tokens';

export interface Theme {
  colors: ColorTokens;
  tones: typeof tones;
  rankTiers: typeof rankTiers;
  spacing: typeof spacing;
  radii: typeof radii;
  typography: typeof typography;
  typeScale: typeof typeScale;
  elevation: typeof elevation;
  focusRing: typeof focusRing;
  motion: typeof motion;
}

/**
 * Release 1.0 chỉ có giao diện sáng; team không làm dark mode. Theme tĩnh, không cần state —
 * vẫn đi qua Context để component dùng useTheme() thống nhất.
 */
export const theme: Theme = {
  colors: colorTokens,
  tones,
  rankTiers,
  spacing,
  radii,
  typography,
  typeScale,
  elevation,
  focusRing,
  motion,
};

export const ThemeContext = createContext<Theme>(theme);

/** Lấy theme hiện tại (màu, tone, spacing, radii, typography, typeScale, elevation, motion). */
export function useTheme(): Theme {
  return useContext(ThemeContext);
}
