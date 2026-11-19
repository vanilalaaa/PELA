import React from 'react';
import { ActivityIndicator, Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { usePressScale } from './usePressScale';

export type ButtonVariant = 'primary' | 'secondary' | 'soft' | 'ghost' | 'star' | 'success' | 'danger' | 'dangerSolid' | 'warn';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps {
  label: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  size?: ButtonSize;
  disabled?: boolean;
  /** Figma không vẽ Loading → dựng theo token (GAP): spinner màu chữ, không nhận nhấn. */
  loading?: boolean;
  /** Icon trái/phải (UIIcon hoặc PelaIcon), kích thước theo size: 16/20/24. */
  renderLeftIcon?: (color: string, size: number) => React.ReactNode;
  renderRightIcon?: (color: string, size: number) => React.ReactNode;
  /** Chiếm hết chiều ngang khung chứa (CTA cuối màn). */
  fullWidth?: boolean;
  accessibilityLabel?: string;
  testID?: string;
}

// PELA/Button 474:6006: SM 36/16/6/16 · MD 48/22/8/20 · LG 56/28/10/24 (cao/padding ngang/gap/icon).
const SIZES = {
  sm: { height: 36, paddingX: 16, gap: 6, icon: 16, type: 'buttonSm' },
  md: { height: 48, paddingX: 22, gap: 8, icon: 20, type: 'buttonMd' },
  lg: { height: 56, paddingX: 28, gap: 10, icon: 24, type: 'buttonLg' },
} as const;

const GLOW = { primary: 'primary', star: 'star', success: 'success' } as const;

/**
 * Nút PELA (Figma PELA/Button 474:6006 + Button / Danger solid 575:11744): pill, Baloo 2 Bold.
 * Pressed = nền đậm hơn, tắt glow, scale .96. Disabled = nền ink-200, chữ ink-400, không viền/bóng.
 */
export function Button({
  label,
  onPress,
  variant = 'primary',
  size = 'md',
  disabled = false,
  loading = false,
  renderLeftIcon,
  renderRightIcon,
  fullWidth = false,
  accessibilityLabel,
  testID,
}: ButtonProps) {
  const theme = useTheme();
  const press = usePressScale(theme.motion.pressScale.button);
  const inactive = disabled || loading;
  const s = SIZES[size];
  const c = theme.componentColors.button[variant];
  const fg = disabled ? theme.componentColors.button.disabled.fg : c.fg;
  const glowKey = variant in GLOW ? GLOW[variant as keyof typeof GLOW] : undefined;
  const border = 'border' in c && !disabled ? c.border : undefined;

  return (
    <Animated.View style={[fullWidth ? styles.fullWidth : styles.hug, press.scaleStyle]}>
      <Pressable
        testID={testID}
        onPress={inactive ? undefined : onPress}
        onPressIn={inactive ? undefined : press.onPressIn}
        onPressOut={inactive ? undefined : press.onPressOut}
        disabled={inactive}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityState={{ disabled: inactive, busy: loading }}
        style={({ pressed }) => [
          styles.base,
          {
            height: s.height,
            minHeight: s.height,
            paddingHorizontal: s.paddingX,
            gap: s.gap,
            borderRadius: theme.radii.full,
            backgroundColor: disabled ? theme.componentColors.button.disabled.bg : pressed ? c.bgPressed : c.bg,
            borderWidth: border ? 2 : 0,
            borderColor: border,
          },
          glowKey && !disabled && !pressed ? theme.elevation[glowKey] : null,
        ]}
      >
        {loading ? (
          <ActivityIndicator color={fg} />
        ) : (
          <>
            {renderLeftIcon ? <View>{renderLeftIcon(fg, s.icon)}</View> : null}
            <Text numberOfLines={1} style={[theme.typeScale[s.type], { color: fg }]}>
              {label}
            </Text>
            {renderRightIcon ? <View>{renderRightIcon(fg, s.icon)}</View> : null}
          </>
        )}
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  base: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  fullWidth: { alignSelf: 'stretch' },
  hug: { alignSelf: 'flex-start' },
});
