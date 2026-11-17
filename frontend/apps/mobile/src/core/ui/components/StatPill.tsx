import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme';
import { PelaIcon } from '../icons';
import { formatStatNumber } from '../logic/display';

export type StatPillKind = 'streak' | 'star';

export interface StatPillProps {
  kind: StatPillKind;
  /** Giá trị do server/use case trả về; component không tự tính (BR-23). */
  value: number;
  /** Nhãn phụ (vd. "ngày"). */
  label?: string;
  testID?: string;
}

/**
 * Chỉ số trên thanh trên cùng (Figma 481:5773): pill trắng cao 36, shadow-sm, pl 4 pr 12, gap 6;
 * icon 24 không nền (Streak = bé lửa, Star = sao amber) + số Baloo ExtraBold 16 màu tone. Số dùng dấu chấm.
 */
export function StatPill({ kind, value, label, testID }: StatPillProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const text = formatStatNumber(value);
  const color = kind === 'streak' ? theme.tones.streak.fg : theme.colors.starFg;

  return (
    <View
      testID={testID}
      accessible
      accessibilityLabel={t(`ui.statPill.${kind}`, { value: text })}
      style={[styles.pill, { borderRadius: theme.radii.full, backgroundColor: theme.colors.bgSurface }, theme.elevation.sm]}
    >
      <PelaIcon name={kind === 'streak' ? 'Streak' : 'Star'} size={24} />
      <Text style={[theme.typeScale.statValue, { color }]}>{text}</Text>
      {label ? <Text style={[theme.typeScale.micro, styles.label, { color: theme.colors.textTertiary }]}>{label}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  pill: { height: 36, paddingLeft: 4, paddingRight: 12, gap: 6, flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start' },
  // Figma: Nunito Bold 12/16.
  label: { fontSize: 12, lineHeight: 16 },
});
