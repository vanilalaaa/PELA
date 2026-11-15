import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, type PressableStateCallbackType } from 'react-native';
import { useTheme } from '../theme';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost';

export interface ButtonProps {
  label: string;
  onPress?: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  testID?: string;
}

/**
 * Button — có 3 variant (primary/secondary/ghost), mỗi variant hỗ trợ đủ 3 state:
 * default / pressed / disabled, và tự đổi màu theo theme light/dark qua useTheme().
 */
export function Button({ label, onPress, variant = 'primary', disabled = false, loading = false, testID }: ButtonProps) {
  const theme = useTheme();
  const isDisabled = disabled || loading;

  const getBackground = (pressed: boolean) => {
    if (variant === 'ghost') return 'transparent';
    // Figma: Disabled = nền ink-200, chữ ink-400, không bóng (mọi variant).
    if (isDisabled) return theme.colors.bgDisabled;
    if (variant === 'primary') return pressed ? theme.colors.bgPrimaryPress : theme.colors.bgPrimary;
    return pressed ? theme.colors.secondaryPressed : theme.colors.secondary;
  };

  const getTextColor = (pressed: boolean) => {
    if (isDisabled) return theme.colors.textDisabled;
    if (variant === 'ghost') return pressed ? theme.colors.bgPrimaryPress : theme.colors.bgPrimary;
    return theme.colors.textOnPrimary;
  };

  const getBorder = () => {
    if (variant === 'ghost') return theme.colors.bgPrimary;
    return 'transparent';
  };

  return (
    <Pressable
      testID={testID}
      onPress={isDisabled ? undefined : onPress}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      style={({ pressed }: PressableStateCallbackType) => [
        styles.base,
        {
          backgroundColor: getBackground(pressed),
          borderColor: getBorder(),
          borderWidth: variant === 'ghost' ? 1 : 0,
          paddingVertical: theme.spacing.sm + 4,
          paddingHorizontal: theme.spacing.lg,
          // Tạm giữ bo 16; Figma dùng radius/full (pill) cho button — đổi ở bước components.
          borderRadius: theme.radii.xl,
          opacity: pressed && !isDisabled ? 0.9 : 1,
          // PELA/shadow-primary — chỉ CTA chính (primary, chưa disabled) có glow shadow theo Figma.
          ...(variant === 'primary' && !isDisabled ? theme.elevation.primary : null),
        },
      ]}
    >
      {({ pressed }: PressableStateCallbackType) =>
        loading ? (
          <ActivityIndicator color={getTextColor(pressed)} />
        ) : (
          <Text
            style={[
              styles.label,
              {
                color: getTextColor(pressed),
                // PELA/button-lg (Figma): Baloo 2 Bold 18/22, letterSpacing 1%.
                fontFamily: theme.typeScale.buttonLg.fontFamily,
                fontSize: theme.typeScale.buttonLg.fontSize,
                lineHeight: theme.typeScale.buttonLg.lineHeight,
                letterSpacing: theme.typeScale.buttonLg.letterSpacing,
              },
            ]}
          >
            {label}
          </Text>
        )
      }
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
  },
  label: {
    textAlign: 'center',
  },
});
