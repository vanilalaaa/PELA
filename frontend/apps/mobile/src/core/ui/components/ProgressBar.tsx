import React, { useEffect, useState } from 'react';
import { Animated, Easing, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { clampProgress } from '../logic/display';

export type ProgressTone = 'blue' | 'amber' | 'mint' | 'coral' | 'streak';

export interface ProgressBarProps {
  /** 0..1, ngoài khoảng sẽ bị kẹp. */
  progress: number;
  tone?: ProgressTone;
  /** Nhãn bên phải (vd. "3/5"). */
  label?: string;
  testID?: string;
}

const HEIGHT = 14;

/**
 * Thanh tiến độ pill (Figma 503:9449): track ink-100 cao 14, fill màu tone có vệt sáng trắng 45%
 * (cao 3.1, cách mép 7, cách trên 2.8). Nhãn Baloo ExtraBold 14. Fill chạy ease-soft 360ms.
 */
export function ProgressBar({ progress, tone = 'blue', label, testID }: ProgressBarProps) {
  const theme = useTheme();
  const value = clampProgress(progress);
  const [anim] = useState(() => new Animated.Value(value));
  const [x1, y1, x2, y2] = theme.motion.easing.soft;

  useEffect(() => {
    Animated.timing(anim, {
      toValue: value,
      duration: theme.motion.duration.slow,
      easing: Easing.bezier(x1, y1, x2, y2),
      useNativeDriver: false,
    }).start();
  }, [anim, value, theme.motion.duration.slow, x1, y1, x2, y2]);

  const width = anim.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] });
  const c = theme.componentColors.progress;

  return (
    <View
      testID={testID}
      style={styles.row}
      accessibilityRole="progressbar"
      accessibilityValue={{ now: Math.round(value * 100), min: 0, max: 100, text: label }}
    >
      <View style={[styles.track, { borderRadius: theme.radii.full, backgroundColor: c.track }]}>
        <Animated.View
          style={[styles.fill, { width, borderRadius: theme.radii.full, backgroundColor: theme.tones[tone].accent }]}
        >
          {value > 0 ? <View style={[styles.highlight, { backgroundColor: c.highlight }]} /> : null}
        </Animated.View>
      </View>
      {label ? <Text style={[theme.typeScale.progressLabel, { color: theme.colors.textSecondary }]}>{label}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, width: '100%' },
  track: { flex: 1, height: HEIGHT, overflow: 'hidden' },
  fill: { height: HEIGHT, minWidth: 0 },
  highlight: { position: 'absolute', left: 7, right: 7, top: 2.8, height: 3.1, borderRadius: 2 },
});
