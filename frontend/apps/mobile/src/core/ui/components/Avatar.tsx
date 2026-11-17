import React from 'react';
import { Image, StyleSheet, Text, View, type ImageSourcePropType } from 'react-native';
import { useTheme } from '../theme';
import { avatarInitial } from '../logic/display';

export type AvatarTone = 'blue' | 'amber' | 'mint' | 'coral' | 'lilac';
export type AvatarSize = 32 | 44 | 56 | 72;

export interface AvatarProps {
  /** Tên đầy đủ: dùng làm nhãn a11y và lấy chữ cái đầu khi không có ảnh. */
  name: string;
  source?: ImageSourcePropType;
  tone?: AvatarTone;
  size?: AvatarSize;
  /** Vòng trắng 3px + blue-500 3px (người dùng hiện tại). */
  ring?: boolean;
  testID?: string;
}

// Avatar 478:5590: chữ Baloo ExtraBold 13/18/24/30 theo cỡ 32/44/56/72.
const FONT: Record<AvatarSize, number> = { 32: 13, 44: 18, 56: 24, 72: 30 };
const RING = 3;

/** Ảnh đại diện tròn; không có ảnh thì hiện chữ cái đầu trên nền tông 100, chữ tông 700. */
export function Avatar({ name, source, tone = 'blue', size = 44, ring = false, testID }: AvatarProps) {
  const theme = useTheme();
  const c = theme.tones[tone];
  const fontSize = FONT[size];
  const circle = { width: size, height: size, borderRadius: theme.radii.full };

  const body = source ? (
    <Image source={source} style={circle} accessibilityIgnoresInvertColors />
  ) : (
    <View style={[circle, styles.center, { backgroundColor: c.bg }]}>
      <Text
        style={{ fontFamily: theme.typography.fontFamily.balooExtraBold, fontSize, lineHeight: fontSize * 1.3, color: c.fg }}
      >
        {avatarInitial(name)}
      </Text>
    </View>
  );

  if (!ring) {
    return (
      <View testID={testID} accessible accessibilityRole="image" accessibilityLabel={name}>
        {body}
      </View>
    );
  }
  const r = theme.componentColors.avatarRing;
  return (
    <View
      testID={testID}
      accessible
      accessibilityRole="image"
      accessibilityLabel={name}
      style={[styles.center, { padding: RING, borderRadius: theme.radii.full, backgroundColor: r.outer }]}
    >
      <View style={[styles.center, { padding: RING, borderRadius: theme.radii.full, backgroundColor: r.inner }]}>{body}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  center: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
});
