import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useTheme } from '../theme';

export type QuestionOptionState = 'default' | 'selected' | 'correct' | 'incorrect' | 'disabled';

export interface QuestionOptionProps {
  label: string;
  state?: QuestionOptionState;
  onPress?: () => void;
  testID?: string;
}

/**
 * QuestionOption — 1 lựa chọn MCQ (Challenge, Listening). State hiển thị kết quả
 * (correct/incorrect) chỉ nên dùng SAU khi có kết quả chấm từ server, không tự suy ra ở client.
 */
export function QuestionOption({ label, state = 'default', onPress, testID }: QuestionOptionProps) {
  const theme = useTheme();
  const disabled = state === 'disabled' || state === 'correct' || state === 'incorrect';

  const getColors = () => {
    switch (state) {
      case 'selected':
        return { bg: theme.colors.surfaceVariant, border: theme.colors.primary, text: theme.colors.textPrimary };
      case 'correct':
        return { bg: theme.colors.success, border: theme.colors.success, text: theme.colors.textOnPrimary };
      case 'incorrect':
        return { bg: theme.colors.danger, border: theme.colors.danger, text: theme.colors.textOnPrimary };
      case 'disabled':
        return { bg: theme.colors.disabledBackground, border: theme.colors.border, text: theme.colors.disabledText };
      default:
        return { bg: theme.colors.background, border: theme.colors.border, text: theme.colors.textPrimary };
    }
  };

  const { bg, border, text } = getColors();

  return (
    <Pressable
      testID={testID}
      onPress={disabled ? undefined : onPress}
      disabled={disabled}
      accessibilityRole="radio"
      accessibilityState={{ selected: state === 'selected', disabled }}
      style={({ pressed }) => [
        styles.base,
        {
          backgroundColor: bg,
          borderColor: border,
          borderRadius: theme.radius.answer, // radius/answer (Figma) — không phải radius.md
          paddingVertical: theme.spacing.sm + 2,
          paddingHorizontal: theme.spacing.md,
          opacity: pressed && !disabled ? 0.85 : 1,
        },
      ]}
    >
      <Text
        style={{
          color: text,
          fontFamily: theme.textStyles.body.fontFamily,
          fontWeight: theme.textStyles.bodyBold.fontWeight,
          fontSize: theme.textStyles.body.fontSize,
          lineHeight: theme.textStyles.body.lineHeight,
        }}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    width: '100%',
    borderWidth: 1,
    minHeight: 44,
    justifyContent: 'center',
  },
});
