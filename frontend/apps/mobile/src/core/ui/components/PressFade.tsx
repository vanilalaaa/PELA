import React, { useState } from 'react';
import { Animated, Easing, Pressable, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from '../theme';

export interface PressFadeProps extends Omit<PressableProps, 'style' | 'children'> {
  children: React.ReactNode;
  /** Style của vùng bấm (áp lên Pressable). */
  style?: StyleProp<ViewStyle>;
}

/**
 * Pressable mờ nhẹ khi nhấn (motion.pressOpacity, thời lượng motion.duration.fast) — dùng cho mục
 * Tabs/BottomNav, nơi Figma không vẽ trạng thái Pressed (GAP-22). Nội bộ, không export ra index.
 */
export function PressFade({ children, style, onPressIn, onPressOut, ...rest }: PressFadeProps) {
  const theme = useTheme();
  const [opacity] = useState(() => new Animated.Value(1));
  const [x1, y1, x2, y2] = theme.motion.easing.soft;
  const animate = (toValue: number) =>
    Animated.timing(opacity, {
      toValue,
      duration: theme.motion.duration.fast,
      easing: Easing.bezier(x1, y1, x2, y2),
      useNativeDriver: true,
    }).start();

  return (
    <Pressable
      {...rest}
      onPressIn={(e) => {
        animate(theme.motion.pressOpacity);
        onPressIn?.(e);
      }}
      onPressOut={(e) => {
        animate(1);
        onPressOut?.(e);
      }}
      style={style}
    >
      <Animated.View style={{ opacity, alignItems: 'center', gap: 3 }}>{children}</Animated.View>
    </Pressable>
  );
}
