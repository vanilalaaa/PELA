import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';

/** Phía đuôi chỉ về người nói: góc dưới trái/phải bo 6, hoặc mũi nhọn giữa đáy. */
export type ChatBubbleTail = 'bottom-left' | 'bottom-right' | 'bottom';
export type ChatBubbleSize = 'sm' | 'md';

export interface ChatBubbleProps {
  message: string;
  /** Tên người nói (vd. "Thỏ PELA"); bỏ trống để ẩn. */
  speaker?: string;
  tail?: ChatBubbleTail;
  /** md: padding 16/12, chữ body-bold · sm: padding 12/8, chữ caption-bold (09.1). */
  size?: ChatBubbleSize;
  testID?: string;
}

const POINTER = 14;

/**
 * Bong bóng thoại của mascot (Figma ChatBubble 457:1684, tone Sky): nền sky-100, bo 20, góc phía đuôi 6
 * (hoặc mũi nhọn giữa đáy), rộng tối đa 280; chữ blue-800.
 */
export function ChatBubble({ message, speaker, tail = 'bottom-left', size = 'md', testID }: ChatBubbleProps) {
  const theme = useTheme();
  const c = theme.componentColors.chatBubble;
  const r = theme.radii.bubble;
  const tailR = theme.radii.bubbleTail;
  return (
    <View
      testID={testID}
      accessible
      accessibilityLabel={speaker ? `${speaker}: ${message}` : message}
      style={[
        styles.bubble,
        size === 'sm' ? styles.sm : styles.md,
        {
          backgroundColor: c.bg,
          borderTopLeftRadius: r,
          borderTopRightRadius: r,
          borderBottomLeftRadius: tail === 'bottom-left' ? tailR : r,
          borderBottomRightRadius: tail === 'bottom-right' ? tailR : r,
        },
      ]}
    >
      {speaker ? <Text style={[theme.typeScale.labelSpeaker, { color: c.speaker }]}>{speaker}</Text> : null}
      <Text style={[size === 'sm' ? theme.typeScale.captionBold : theme.typeScale.bodyBold, { color: c.fg }]}>{message}</Text>
      {tail === 'bottom' ? (
        <View pointerEvents="none" style={[styles.pointer, { backgroundColor: c.bg, borderRadius: 3 }]} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  bubble: { maxWidth: 280, gap: 2 },
  md: { paddingHorizontal: 16, paddingVertical: 12 },
  sm: { paddingHorizontal: 12, paddingVertical: 8 },
  pointer: {
    position: 'absolute',
    width: POINTER,
    height: POINTER,
    bottom: -POINTER / 2 + 1,
    left: '50%',
    marginLeft: -POINTER / 2,
    transform: [{ rotate: '45deg' }],
  },
});
