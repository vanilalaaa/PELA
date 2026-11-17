import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';

export interface ChatBubbleProps {
  message: string;
  /** Tên người nói (vd. "Thỏ PELA"); bỏ trống để ẩn. */
  speaker?: string;
  testID?: string;
}

/**
 * Bong bóng thoại của mascot (Figma ChatBubble 457:1684, tone Sky, đuôi Bottom-left): nền sky-100, bo 20,
 * góc dưới trái 6 (chỉ về mascot), padding 16/12, rộng tối đa 280; chữ bodyBold blue-800.
 */
export function ChatBubble({ message, speaker, testID }: ChatBubbleProps) {
  const theme = useTheme();
  const c = theme.componentColors.chatBubble;
  return (
    <View
      testID={testID}
      accessible
      accessibilityLabel={speaker ? `${speaker}: ${message}` : message}
      style={[
        styles.bubble,
        {
          backgroundColor: c.bg,
          borderTopLeftRadius: theme.radii.bubble,
          borderTopRightRadius: theme.radii.bubble,
          borderBottomRightRadius: theme.radii.bubble,
          borderBottomLeftRadius: theme.radii.bubbleTail,
        },
      ]}
    >
      {speaker ? <Text style={[theme.typeScale.labelSpeaker, { color: c.speaker }]}>{speaker}</Text> : null}
      <Text style={[theme.typeScale.bodyBold, { color: c.fg }]}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  bubble: { maxWidth: 280, paddingHorizontal: 16, paddingVertical: 12, gap: 2, alignSelf: 'flex-start' },
});
