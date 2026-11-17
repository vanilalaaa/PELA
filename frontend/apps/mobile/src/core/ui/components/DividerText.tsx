import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';

/** Đường kẻ có chữ ở giữa (vd. "hoặc tiếp tục với", Figma 01/02/05): kẻ 1px bgDisabled, chữ captionBold textTertiary, gap 12. */
export function DividerText({ label }: { label: string }) {
  const theme = useTheme();
  const line = [styles.line, { backgroundColor: theme.colors.bgDisabled }];
  return (
    <View style={styles.row} accessible accessibilityRole="text" accessibilityLabel={label}>
      <View style={line} />
      <Text style={[theme.typeScale.captionBold, { color: theme.colors.textTertiary }]}>{label}</Text>
      <View style={line} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, width: '100%' },
  line: { flex: 1, height: 1 },
});
