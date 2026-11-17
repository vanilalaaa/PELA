import React, { useEffect, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';

export interface RadioProps {
  checked: boolean;
  onPress?: () => void;
  label?: string;
  disabled?: boolean;
  accessibilityLabel?: string;
  testID?: string;
}

const RING = 26;
const DOT = 14;

/**
 * Radio (Figma 476:5515): vòng 26, viền 2px (blue-500 khi chọn, ink-300 khi chưa), chấm 14
 * blue-500 bật bằng ease-bounce. Disabled: vòng mờ 50%, nhãn ink-400. Vùng chạm ≥ 44.
 */
export function Radio({ checked, onPress, label, disabled = false, accessibilityLabel, testID }: RadioProps) {
  const theme = useTheme();
  const control = theme.componentColors.control;
  const [dot] = useState(() => new Animated.Value(checked ? 1 : 0));
  const [x1, y1, x2, y2] = theme.motion.easing.bounce;

  useEffect(() => {
    Animated.timing(dot, {
      toValue: checked ? 1 : 0,
      duration: theme.motion.duration.base,
      easing: Easing.bezier(x1, y1, x2, y2),
      useNativeDriver: true,
    }).start();
  }, [checked, dot, theme.motion.duration.base, x1, y1, x2, y2]);

  return (
    <Pressable
      testID={testID}
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
      accessibilityRole="radio"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ checked, disabled }}
      style={[styles.row, { minHeight: theme.spacing.tapMin }]}
    >
      <View
        style={[
          styles.ring,
          {
            borderRadius: theme.radii.full,
            borderColor: checked ? control.on : control.offBorder,
            backgroundColor: theme.colors.bgSurface,
          },
          disabled && styles.dim,
        ]}
      >
        <Animated.View
          style={{
            width: DOT,
            height: DOT,
            borderRadius: theme.radii.full,
            backgroundColor: control.on,
            transform: [{ scale: dot }],
          }}
        />
      </View>
      {label ? (
        <Text style={[theme.typeScale.bodyBold, { color: disabled ? theme.colors.textDisabled : theme.colors.textPrimary }]}>
          {label}
        </Text>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, alignSelf: 'flex-start' },
  ring: { width: RING, height: RING, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  dim: { opacity: 0.5 },
});
