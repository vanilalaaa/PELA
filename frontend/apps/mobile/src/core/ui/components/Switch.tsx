import React, { useEffect, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';

export interface SwitchProps {
  value: boolean;
  onChange?: (value: boolean) => void;
  label?: string;
  disabled?: boolean;
  accessibilityLabel?: string;
  testID?: string;
}

const TRACK_W = 52;
const TRACK_H = 32;
const KNOB = 26;
const INSET = (TRACK_H - KNOB) / 2;

/**
 * Switch (Figma 476:5532): track 52×32 pill (blue-500 khi bật, ink-300 khi tắt), núm trắng 26 có
 * bóng nhẹ, trượt bằng ease-bounce. Disabled: cả hàng mờ 50%. Vùng chạm ≥ 44.
 */
export function Switch({ value, onChange, label, disabled = false, accessibilityLabel, testID }: SwitchProps) {
  const theme = useTheme();
  const control = theme.componentColors.control;
  const [progress] = useState(() => new Animated.Value(value ? 1 : 0));
  const [x1, y1, x2, y2] = theme.motion.easing.bounce;

  useEffect(() => {
    Animated.timing(progress, {
      toValue: value ? 1 : 0,
      duration: theme.motion.duration.base,
      easing: Easing.bezier(x1, y1, x2, y2),
      useNativeDriver: true,
    }).start();
  }, [value, progress, theme.motion.duration.base, x1, y1, x2, y2]);

  const translateX = progress.interpolate({ inputRange: [0, 1], outputRange: [0, TRACK_W - KNOB - INSET * 2] });

  return (
    <Pressable
      testID={testID}
      onPress={disabled ? undefined : () => onChange?.(!value)}
      disabled={disabled}
      accessibilityRole="switch"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ checked: value, disabled }}
      style={[styles.row, { minHeight: theme.spacing.tapMin }, disabled && styles.dim]}
    >
      <View
        style={[
          styles.track,
          { borderRadius: theme.radii.full, backgroundColor: value ? control.on : control.switchOffTrack },
        ]}
      >
        <Animated.View
          style={[
            styles.knob,
            { borderRadius: theme.radii.full, backgroundColor: control.knob, transform: [{ translateX }] },
            theme.elevation.xs,
          ]}
        />
      </View>
      {label ? <Text style={[theme.typeScale.bodyBold, { color: theme.colors.textPrimary }]}>{label}</Text> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, alignSelf: 'flex-start' },
  track: { width: TRACK_W, height: TRACK_H, padding: INSET, justifyContent: 'center' },
  knob: { width: KNOB, height: KNOB },
  dim: { opacity: 0.5 },
});
