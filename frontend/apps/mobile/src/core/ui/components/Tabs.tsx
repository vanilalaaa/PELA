import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { PressFade } from './PressFade';

export interface TabItem<K extends string> {
  key: K;
  label: string;
}

export interface TabsProps<K extends string> {
  /** 2 hoặc 3 mục (Figma Items=2|3). */
  items: readonly TabItem<K>[];
  value: K;
  onChange: (key: K) => void;
  testID?: string;
}

/**
 * Tabs dạng segmented (Figma 480:5609): nền ink-100 pill cao 48, padding 4, gap 4; tab chọn = trắng +
 * shadow-sm, chữ blue-700; tab khác chữ ink-500. Baloo 2 Bold 15. Mỗi tab cao 40 (vùng chạm ≥ 44 nhờ
 * padding của nền).
 */
export function Tabs<K extends string>({ items, value, onChange, testID }: TabsProps<K>) {
  const theme = useTheme();
  const c = theme.componentColors.tabs;
  return (
    <View
      testID={testID}
      accessibilityRole="tablist"
      style={[styles.track, { borderRadius: theme.radii.full, backgroundColor: c.track }]}
    >
      {items.map((item) => {
        const active = item.key === value;
        return (
          <PressFade
            key={item.key}
            onPress={() => onChange(item.key)}
            hitSlop={{ top: 4, bottom: 4 }}
            accessibilityRole="tab"
            accessibilityLabel={item.label}
            accessibilityState={{ selected: active }}
            style={[
              styles.tab,
              { borderRadius: theme.radii.full },
              active ? [{ backgroundColor: c.active }, theme.elevation.sm] : null,
            ]}
          >
            <Text style={[theme.typeScale.tab, { color: active ? c.fgActive : c.fg }]} numberOfLines={1}>
              {item.label}
            </Text>
          </PressFade>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 48, padding: 4, gap: 4, flexDirection: 'row', width: '100%' },
  tab: { flex: 1, height: 40, alignItems: 'center', justifyContent: 'center' },
});
