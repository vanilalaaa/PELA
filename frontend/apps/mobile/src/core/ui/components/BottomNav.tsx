import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme';
import { PressFade } from './PressFade';
import { PelaIcon, type PelaIconName } from '../icons';
import { formatBadgeCount } from '../logic/display';

export type BottomNavTab = 'home' | 'lesson' | 'practice' | 'leaderboard' | 'profile';

export interface BottomNavProps {
  active: BottomNavTab;
  onChange: (tab: BottomNavTab) => void;
  /** Số trên mục Xếp hạng; bỏ trống hoặc 0 để ẩn. */
  rankBadgeCount?: number;
  /** Inset dưới (safe area) do khung màn truyền vào; cộng thêm vào padding đáy. */
  bottomInset?: number;
  testID?: string;
}

const TABS: readonly { key: BottomNavTab; icon: PelaIconName }[] = [
  { key: 'home', icon: 'Home' },
  { key: 'lesson', icon: 'Lesson' },
  { key: 'practice', icon: 'Practice' },
  { key: 'leaderboard', icon: 'Leaderboard' },
  { key: 'profile', icon: 'Profile' },
];

/**
 * Thanh điều hướng dưới (Figma 480:5870): cao 76 (spacing/bottom-nav-h), nền trắng, viền trên ink-200,
 * pt 6 pb 14 px 8. Mục chọn: pill 56×32 blue-50 + PelaIcon Default + nhãn blue-700; mục khác:
 * PelaIcon Inactive + nhãn ink-400 (Nunito ExtraBold 11). Badge coral-500 trên Xếp hạng.
 */
export function BottomNav({ active, onChange, rankBadgeCount, bottomInset = 0, testID }: BottomNavProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const c = theme.componentColors.bottomNav;
  const showBadge = rankBadgeCount !== undefined && rankBadgeCount > 0;

  return (
    <View
      testID={testID}
      accessibilityRole="tablist"
      style={[
        styles.bar,
        { height: theme.spacing.bottomNavH + bottomInset, paddingBottom: 14 + bottomInset, backgroundColor: c.bg, borderTopColor: c.border },
      ]}
    >
      {TABS.map(({ key, icon }) => {
        const selected = key === active;
        const label = t(`ui.bottomNav.${key}`);
        return (
          <PressFade
            key={key}
            onPress={() => onChange(key)}
            accessibilityRole="tab"
            accessibilityLabel={
              key === 'leaderboard' && showBadge ? `${label}, ${t('ui.badge.count', { count: formatBadgeCount(rankBadgeCount) })}` : label
            }
            accessibilityState={{ selected }}
            style={styles.item}
          >
            <View style={[styles.pill, { borderRadius: theme.radii.full }, selected && { backgroundColor: c.pill }]}>
              <PelaIcon name={icon} size={24} state={selected ? 'default' : 'inactive'} />
            </View>
            <Text style={[theme.typeScale.navLabel, { color: selected ? c.fgActive : c.fg }]} numberOfLines={1}>
              {label}
            </Text>
            {key === 'leaderboard' && showBadge ? (
              <View style={[styles.badge, { borderRadius: theme.radii.full, backgroundColor: c.badge, borderColor: c.bg }]}>
                <Text style={[theme.typeScale.navLabel, { color: c.bg }]}>{formatBadgeCount(rankBadgeCount, 9)}</Text>
              </View>
            ) : null}
          </PressFade>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', paddingTop: 6, paddingHorizontal: 8, borderTopWidth: 1, width: '100%' },
  item: { flex: 1, alignItems: 'center', gap: 3, minHeight: 44, justifyContent: 'center' },
  pill: { width: 56, height: 32, alignItems: 'center', justifyContent: 'center' },
  // Figma: badge 18, viền trắng 2, đặt lệch phải pill (left 54.75 trong mục rộng ~75), top -2.
  badge: { position: 'absolute', top: -2, left: '50%', marginLeft: 17, minWidth: 18, height: 18, paddingHorizontal: 4, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
});
