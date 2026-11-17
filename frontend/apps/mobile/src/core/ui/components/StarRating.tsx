import React from 'react';
import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme';
import { PelaIcon } from '../icons';
import { clampStars } from '../logic/display';

export interface StarRatingProps {
  /** Số sao đã đạt (0..max), do server tính (BR-36/BR-20). */
  value: number;
  /** Daily/Lesson = 5, Weekly = 6 (BR-36). */
  max?: number;
  size?: number;
  testID?: string;
}

/**
 * StarRating — CHỈ hiển thị: sao đạt = PelaIcon Star Default (amber), sao chưa đạt = Star Inactive.
 * Không cho bấm chọn vì số sao do server tính (BR-23).
 */
export function StarRating({ value, max = 5, size = 24, testID }: StarRatingProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const filled = clampStars(value, max);

  return (
    <View
      testID={testID}
      style={[styles.row, { gap: theme.spacing.xs }]}
      accessible
      accessibilityRole="image"
      accessibilityLabel={t('ui.starRating', { value: filled, max })}
    >
      {Array.from({ length: max }, (_, i) => (
        <PelaIcon key={i} name="Star" size={size} state={i < filled ? 'default' : 'inactive'} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
});
