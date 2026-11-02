import React from 'react';
import { StyleSheet, View } from 'react-native';
import { useTheme } from '../theme';

export interface ProgressBarProps {
  /** 0..1 */
  progress: number;
  color?: 'primary' | 'success' | 'warning';
  heightPx?: number;
  testID?: string;
}

/** ProgressBar — dùng cho tiến độ lesson/topic. progress được clamp về [0,1]. */
export function ProgressBar({ progress, color = 'primary', heightPx = 8, testID }: ProgressBarProps) {
  const theme = useTheme();
  const clamped = Math.max(0, Math.min(1, progress));

  const fillColor =
    color === 'success' ? theme.colors.success : color === 'warning' ? theme.colors.warning : theme.colors.primary;

  return (
    <View
      testID={testID}
      accessibilityRole="progressbar"
      accessibilityValue={{ now: Math.round(clamped * 100), min: 0, max: 100 }}
      style={[
        styles.track,
        {
          height: heightPx,
          borderRadius: heightPx / 2,
          backgroundColor: theme.colors.surfaceVariant,
        },
      ]}
    >
      <View
        style={{
          width: `${clamped * 100}%`,
          height: '100%',
          borderRadius: heightPx / 2,
          backgroundColor: fillColor,
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: '100%',
    overflow: 'hidden',
  },
});
