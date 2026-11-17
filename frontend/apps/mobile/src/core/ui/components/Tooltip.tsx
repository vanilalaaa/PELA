import React, { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View, type LayoutRectangle } from 'react-native';
import { useTheme } from '../theme';

export interface TooltipProps {
  text: string;
  /** Phần tử kích hoạt (vd. icon Info). Chạm để mở, chạm ra ngoài để đóng. */
  children: React.ReactNode;
  /** Nhãn đọc màn hình của phần tử kích hoạt. */
  accessibilityLabel: string;
  testID?: string;
}

const ARROW = 10;
const GAP = 6;
const EDGE = 16;

/**
 * Tooltip (Figma 479:5715, Placement Top): nền ink-900, chữ captionBold trắng, bo 12, padding 12/8,
 * mũi tên 10 dưới đáy chỉ vào phần tử. Một dòng ngắn. Dùng Modal trong suốt để nằm trên mọi thứ và
 * đóng khi chạm ra ngoài; vị trí tính từ measureInWindow của phần tử kích hoạt.
 */
export function Tooltip({ text, children, accessibilityLabel, testID }: TooltipProps) {
  const theme = useTheme();
  const c = theme.componentColors.tooltip;
  const [anchor, setAnchor] = useState<LayoutRectangle | null>(null);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [ref, setRef] = useState<React.ComponentRef<typeof View> | null>(null);
  const [screenW, setScreenW] = useState(0);

  const open = () => ref?.measureInWindow((x: number, y: number, width: number, height: number) => setAnchor({ x, y, width, height }));
  const close = () => {
    setAnchor(null);
    setSize(null);
  };

  const centerX = anchor ? anchor.x + anchor.width / 2 : 0;
  const left = size ? Math.min(Math.max(EDGE, centerX - size.width / 2), Math.max(EDGE, screenW - EDGE - size.width)) : 0;
  const top = anchor && size ? anchor.y - size.height - ARROW / 2 - GAP : 0;

  return (
    <>
      <Pressable
        ref={setRef}
        testID={testID}
        onPress={open}
        hitSlop={12}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        accessibilityHint={text}
      >
        {children}
      </Pressable>
      <Modal visible={anchor !== null} transparent statusBarTranslucent animationType="fade" onRequestClose={close}>
        <Pressable style={StyleSheet.absoluteFill} onPress={close} onLayout={(e) => setScreenW(e.nativeEvent.layout.width)}>
          <View
            onLayout={(e) => setSize({ width: e.nativeEvent.layout.width, height: e.nativeEvent.layout.height })}
            style={[styles.bubble, { left, top, opacity: size ? 1 : 0, backgroundColor: c.bg, borderRadius: theme.radii.lg }, theme.elevation.md]}
            accessibilityLiveRegion="polite"
          >
            <Text style={[theme.typeScale.captionBold, { color: c.fg }]}>{text}</Text>
            <View
              style={[
                styles.arrow,
                { backgroundColor: c.bg, left: Math.max(ARROW, Math.min(centerX - left - ARROW / 2, (size?.width ?? 0) - ARROW * 2)) },
              ]}
            />
          </View>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  bubble: { position: 'absolute', paddingHorizontal: 12, paddingVertical: 8, maxWidth: 320 },
  arrow: { position: 'absolute', bottom: -ARROW / 2 + 1, width: ARROW, height: ARROW, borderRadius: 2, transform: [{ rotate: '45deg' }] },
});
