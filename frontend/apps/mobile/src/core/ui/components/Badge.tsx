import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme';
import { formatBadgeCount } from '../logic/display';

export type BadgeTone = 'blue' | 'coral' | 'mint' | 'amber' | 'soft' | 'neutral';

export interface BadgeProps {
  /** Bỏ trống để hiện dạng chấm 10px. */
  count?: number;
  tone?: BadgeTone;
  /** Quá số này hiện "max+". */
  max?: number;
  /** Viền trắng 2px (khi đặt chồng lên icon/avatar). Chấm luôn có viền. */
  ring?: boolean;
  testID?: string;
}

/** Badge số / chấm (Figma 503:9333): cao 22, tối thiểu 22, px 7, bo full. Chấm 10px viền trắng 2px. */
export function Badge({ count, tone = 'coral', max = 99, ring = false, testID }: BadgeProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const c = theme.componentColors.badge[tone];
  const ringStyle = { borderWidth: 2, borderColor: theme.componentColors.badge.ring };

  if (count === undefined) {
    return (
      <View
        testID={testID}
        accessibilityLabel={t('ui.badge.dot')}
        style={[styles.dot, ringStyle, { borderRadius: theme.radii.full, backgroundColor: c.bg }]}
      />
    );
  }
  const text = formatBadgeCount(count, max);
  return (
    <View
      testID={testID}
      accessible
      accessibilityLabel={t('ui.badge.count', { count: text })}
      style={[styles.pill, ring && ringStyle, { borderRadius: theme.radii.full, backgroundColor: c.bg }]}
    >
      <Text style={[theme.typeScale.badge, { color: c.fg }]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  dot: { width: 10, height: 10 },
  pill: { height: 22, minWidth: 22, paddingHorizontal: 7, alignItems: 'center', justifyContent: 'center', alignSelf: 'flex-start' },
});
