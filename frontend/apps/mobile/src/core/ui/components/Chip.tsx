import React from 'react';
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from '../theme';

export type ChipTone = 'neutral' | 'sky' | 'mint' | 'amber' | 'coral';
export type ChipAppearance = 'soft' | 'solid';
export type ChipSize = 'sm' | 'md';

export interface ChipProps {
  label: string;
  tone?: ChipTone;
  appearance?: ChipAppearance;
  size?: ChipSize;
  /** Icon tuỳ chọn; nhận màu chữ và cỡ icon của Chip. */
  renderIcon?: (color: string, size: number) => React.ReactNode;
  /** Ghi đè vị trí (vd. alignSelf: 'center'); mặc định canh trái. */
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

// Chip 643:18143: SM cao 26 (px10, gap5, icon 16) · MD cao 30 (px12, gap6, icon 14.4).
const SIZES = {
  sm: { h: 26, px: 10, gap: 5, icon: 16 },
  md: { h: 30, px: 12, gap: 6, icon: 14.4 },
} as const;

/** Nhãn trạng thái chỉ hiển thị (không bấm). Bo full, chữ caption bold. */
export function Chip({ label, tone = 'neutral', appearance = 'soft', size = 'sm', renderIcon, style, testID }: ChipProps) {
  const theme = useTheme();
  const c = theme.componentColors.chip[appearance][tone];
  const s = SIZES[size];
  return (
    <View
      testID={testID}
      style={[styles.chip, { height: s.h, paddingHorizontal: s.px, gap: s.gap, borderRadius: theme.radii.full, backgroundColor: c.bg }, style]}
    >
      {renderIcon ? renderIcon(c.fg, s.icon) : null}
      <Text style={[theme.typeScale.captionBold, styles.label, { color: c.fg }]} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // Không vượt bề ngang khung; chữ dài thì co lại và cắt bằng dấu …
  chip: { flexDirection: 'row', alignItems: 'center', alignSelf: 'flex-start', maxWidth: '100%' },
  label: { flexShrink: 1 },
});
