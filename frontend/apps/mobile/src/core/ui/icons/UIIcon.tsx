import React from 'react';
import { useTheme } from '../theme';
import { IconLayers } from './IconLayers';
import { uiIconData, type UIIconName } from './uiIconData';

export interface UIIconProps {
  name: UIIconName;
  /** Figma có 16/20/24; vector co giãn được nên nhận số bất kỳ. */
  size?: number;
  /** Mặc định color/text/secondary như Figma (UIIcon 473:5427). */
  color?: string;
}

/** Glyph giao diện đơn sắc (Figma UIIcon). Màu đi qua `currentColor` nên đổi bằng prop `color`. */
export function UIIcon({ name, size = 24, color }: UIIconProps) {
  const theme = useTheme();
  return <IconLayers layers={uiIconData[name]} size={size} color={color ?? theme.colors.textSecondary} />;
}
