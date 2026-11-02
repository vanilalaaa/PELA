import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';

export interface StarRatingProps {
  /** Số sao đã đạt (0..max). */
  value: number;
  /** Daily = 5, Weekly = 6 (BR-36). */
  max?: number;
  size?: number;
  testID?: string;
}

/**
 * StarRating — CHỈ hiển thị, không cho bấm chọn: điểm sao do server tính (server-as-arbiter,
 * BR-36/BR-20), client không được tự gán/sửa số sao.
 */
export function StarRating({ value, max = 5, size = 20, testID }: StarRatingProps) {
  const theme = useTheme();
  const clamped = Math.max(0, Math.min(max, Math.round(value)));
  const stars = Array.from({ length: max }, (_, i) => i < clamped);

  return (
    <View testID={testID} style={styles.row} accessibilityLabel={`${clamped}/${max} sao`}>
      {stars.map((filled, i) => (
        <Text
          key={i}
          style={{
            fontSize: size,
            color: filled ? theme.colors.starFilled : theme.colors.starEmpty,
            marginRight: i < stars.length - 1 ? theme.spacing.xs / 2 : 0,
          }}
        >
          {filled ? '★' : '☆'}
        </Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
  },
});
