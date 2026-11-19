import React from 'react';
import { Animated, Pressable } from 'react-native';
import { useTheme } from '../theme';
import { usePressScale } from './usePressScale';

export type IconButtonVariant = 'soft' | 'ghost' | 'primary' | 'white';
export type IconButtonSize = 'sm' | 'md' | 'lg';

export interface IconButtonProps {
  /** Vẽ icon với màu và kích thước glyph do IconButton quyết định (glyph ≈ 50% nút). */
  renderIcon: (color: string, size: number) => React.ReactNode;
  /** Bắt buộc: mô tả hành động cho trình đọc màn hình (Figma: luôn đặt aria-label). */
  accessibilityLabel: string;
  onPress?: () => void;
  variant?: IconButtonVariant;
  size?: IconButtonSize;
  disabled?: boolean;
  testID?: string;
}

// IconButton 475:5355: SM 36 · MD 44 · LG 52; glyph 16/20/24.
const SIZES = { sm: { box: 36, glyph: 16 }, md: { box: 44, glyph: 20 }, lg: { box: 52, glyph: 24 } } as const;

/** Nút tròn chỉ có icon. Pressed: nền đậm hơn + scale .92. White có shadow-sm. */
export function IconButton({
  renderIcon,
  accessibilityLabel,
  onPress,
  variant = 'soft',
  size = 'md',
  disabled = false,
  testID,
}: IconButtonProps) {
  const theme = useTheme();
  const press = usePressScale(theme.motion.pressScale.iconButton);
  const s = SIZES[size];
  const c = theme.componentColors.iconButton[variant];
  const off = theme.componentColors.iconButton.disabled;
  // Vùng chạm tối thiểu 44 (UI-3): nút 36 được nới bằng hitSlop.
  const slop = Math.max(0, (theme.spacing.tapMin - s.box) / 2);

  return (
    <Animated.View style={press.scaleStyle}>
      <Pressable
        testID={testID}
        onPress={disabled ? undefined : onPress}
        onPressIn={disabled ? undefined : press.onPressIn}
        onPressOut={disabled ? undefined : press.onPressOut}
        disabled={disabled}
        hitSlop={slop}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        accessibilityState={{ disabled }}
        style={({ pressed }) => [
          {
            width: s.box,
            height: s.box,
            borderRadius: theme.radii.full,
            alignItems: 'center',
            justifyContent: 'center',
            // Figma: Disabled của mọi variant (kể cả Ghost) là nền ink-100, icon ink-400.
            backgroundColor: disabled
              ? off.bg
              : pressed
                ? c.bgPressed
                : c.bg,
          },
          variant === 'white' && !disabled ? theme.elevation.sm : null,
        ]}
      >
        {renderIcon(disabled ? off.fg : c.fg, s.glyph)}
      </Pressable>
    </Animated.View>
  );
}
