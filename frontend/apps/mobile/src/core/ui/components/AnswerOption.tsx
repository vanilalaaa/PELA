import React from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme';
import { UIIcon } from '../icons';
import { usePressScale } from './usePressScale';

export type AnswerOptionState = 'idle' | 'selected' | 'correct' | 'wrong' | 'disabled';

export interface AnswerOptionProps {
  label: string;
  /** Chữ trên phím (A, B, C…); bỏ trống để ẩn phím. */
  keyLabel?: string;
  state?: AnswerOptionState;
  onPress?: () => void;
  testID?: string;
}

/**
 * Đáp án trắc nghiệm (Figma 481:5935): rộng full, cao tối thiểu 58, bo radius/answer 20, viền 2px,
 * pl 10 pr 16, gap 12. Idle trắng + shadow-xs · Selected blue · Correct mint + phím check · Wrong coral +
 * phím x · Disabled ink-50. Phím 34, bo radius/key 12. Nhấn scale .98.
 * Correct/Wrong chỉ hiển thị kết quả chấm do use case/server trả về, không tự suy ra ở client (BR-23).
 */
export function AnswerOption({ label, keyLabel, state = 'idle', onPress, testID }: AnswerOptionProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const press = usePressScale(theme.motion.pressScale.card);
  const c = theme.componentColors.answer[state];
  const interactive = (state === 'idle' || state === 'selected') && !!onPress;
  const resultLabel = state === 'correct' || state === 'wrong' ? `${label}, ${t(`ui.answer.${state}`)}` : label;

  return (
    <Animated.View style={press.scaleStyle}>
      <Pressable
        testID={testID}
        onPress={interactive ? onPress : undefined}
        onPressIn={interactive ? press.onPressIn : undefined}
        onPressOut={interactive ? press.onPressOut : undefined}
        disabled={!interactive}
        accessibilityRole="radio"
        accessibilityLabel={keyLabel ? `${keyLabel}. ${resultLabel}` : resultLabel}
        accessibilityState={{ selected: state === 'selected', checked: state === 'selected', disabled: state === 'disabled' }}
        style={[
          styles.box,
          { borderRadius: theme.radii.answer, backgroundColor: c.bg, borderColor: c.border },
          state === 'idle' ? theme.elevation.xs : null,
        ]}
      >
        {keyLabel !== undefined ? (
          <View style={[styles.key, { borderRadius: theme.radii.key, backgroundColor: c.keyBg }]}>
            {state === 'correct' ? (
              <UIIcon name="Check" size={20} color={c.keyFg} />
            ) : state === 'wrong' ? (
              <UIIcon name="Close" size={20} color={c.keyFg} />
            ) : (
              <Text style={[theme.typeScale.statValue, { color: c.keyFg }]}>{keyLabel}</Text>
            )}
          </View>
        ) : null}
        <Text style={[theme.typeScale.answerLabel, styles.label, { color: c.fg }]}>{label}</Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  box: { width: '100%', minHeight: 58, borderWidth: 2, paddingLeft: 10, paddingRight: 16, paddingVertical: 10, gap: 12, flexDirection: 'row', alignItems: 'center' },
  key: { width: 34, height: 34, alignItems: 'center', justifyContent: 'center' },
  label: { flex: 1 },
});
