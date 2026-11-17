import { useState } from 'react';
import { Animated, Easing } from 'react-native';
import { useTheme } from '../theme';

/**
 * Hiệu ứng nhấn theo motion.pressScale của Figma (button .96, iconButton .92, card/đáp án .98),
 * thời lượng motion.duration.fast, easing ease-soft. Dùng Animated có sẵn của React Native.
 */
export function usePressScale(scaleTo: number) {
  const theme = useTheme();
  const [scale] = useState(() => new Animated.Value(1));
  const [x1, y1, x2, y2] = theme.motion.easing.soft;
  const easing = Easing.bezier(x1, y1, x2, y2);

  const animate = (toValue: number) =>
    Animated.timing(scale, { toValue, duration: theme.motion.duration.fast, easing, useNativeDriver: true }).start();

  return {
    scaleStyle: { transform: [{ scale }] },
    onPressIn: () => animate(scaleTo),
    onPressOut: () => animate(1),
  };
}
