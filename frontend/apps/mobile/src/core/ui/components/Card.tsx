import React from 'react';
import { Pressable, StyleSheet, View, type ViewStyle } from 'react-native';
import { useTheme } from '../theme';

export interface CardProps {
  children: React.ReactNode;
  onPress?: () => void;
  disabled?: boolean;
  style?: ViewStyle;
  testID?: string;
}

/**
 * Card — khung chứa nội dung (vd. 1 lesson, 1 topic). Nếu có onPress thì render như
 * Pressable (có state pressed/disabled), nếu không thì chỉ là View tĩnh.
 */
export function Card({ children, onPress, disabled = false, style, testID }: CardProps) {
  const theme = useTheme();

  const base: ViewStyle = {
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.lg, // radius/card (Figma)
    padding: theme.spacing.md,
    borderWidth: 1,
    borderColor: theme.colors.border,
    ...theme.shadows.sm, // PELA/shadow-sm — card trong Figma (32 Badges, Practice Hub) đều có shadow nhẹ này
  };

  if (!onPress) {
    return (
      <View testID={testID} style={[styles.base, base, disabled && { opacity: 0.5 }, style]}>
        {children}
      </View>
    );
  }

  return (
    <Pressable
      testID={testID}
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      style={({ pressed }) => [
        styles.base,
        base,
        pressed && !disabled && { backgroundColor: theme.colors.surfaceVariant },
        disabled && { opacity: 0.5 },
        style,
      ]}
    >
      {children}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    width: '100%',
  },
});
