import React from 'react';
import { Animated, Pressable, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from '../theme';
import { usePressScale } from './usePressScale';

export type CardTone = 'white' | 'blue' | 'sky' | 'amber' | 'coral' | 'mint' | 'lilac';

export interface CardProps {
  children?: React.ReactNode;
  title?: string;
  body?: string;
  tone?: CardTone;
  /** Chỉ áp cho tone white: thêm shadow-sm. Tint luôn phẳng (Figma). */
  elevated?: boolean;
  onPress?: () => void;
  disabled?: boolean;
  /** Nhãn a11y khi Card bấm được mà không có title. */
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

/**
 * Card (Figma 503:9474): bo radius/card 24, padding 20, gap 8. White Elevated có shadow-sm; tint
 * (blue-50, sky/amber/coral/mint/lilac-100) phẳng. Tiêu đề h3, nội dung body textSecondary.
 * Có onPress thì nhấn scale .98.
 */
export function Card({
  children,
  title,
  body,
  tone = 'white',
  elevated = true,
  onPress,
  disabled = false,
  accessibilityLabel,
  style,
  testID,
}: CardProps) {
  const theme = useTheme();
  const press = usePressScale(theme.motion.pressScale.card);

  const boxStyle: StyleProp<ViewStyle> = [
    {
      width: '100%',
      borderRadius: theme.radii.card,
      padding: theme.spacing['2md'],
      gap: theme.spacing.sm,
      backgroundColor: theme.componentColors.tint[tone],
    },
    tone === 'white' && elevated ? theme.elevation.sm : null,
    disabled ? { opacity: 0.5 } : null,
    style,
  ];

  const content = (
    <>
      {title ? <Text style={[theme.typeScale.h3, { color: theme.colors.textPrimary }]}>{title}</Text> : null}
      {body ? <Text style={[theme.typeScale.body, { color: theme.colors.textSecondary }]}>{body}</Text> : null}
      {children}
    </>
  );

  if (!onPress) {
    return (
      <View testID={testID} style={boxStyle}>
        {content}
      </View>
    );
  }

  return (
    <Animated.View style={press.scaleStyle}>
      <Pressable
        testID={testID}
        onPress={disabled ? undefined : onPress}
        onPressIn={disabled ? undefined : press.onPressIn}
        onPressOut={disabled ? undefined : press.onPressOut}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel ?? title}
        accessibilityState={{ disabled }}
        style={boxStyle}
      >
        {content}
      </Pressable>
    </Animated.View>
  );
}
