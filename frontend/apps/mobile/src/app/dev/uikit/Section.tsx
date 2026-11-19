import React from 'react';
import { Text, View } from 'react-native';
import { useTheme } from '../../../core/ui/theme';

/** Khung một mục trong trang DevUiKit: tiêu đề + nội dung. Chỉ dùng cho màn dev. */
export function Section({ title, children }: { title: string; children: React.ReactNode }) {
  const theme = useTheme();
  return (
    <View style={{ gap: theme.spacing.sm }}>
      <Text style={[theme.typeScale.h3, { color: theme.colors.textPrimary }]}>{title}</Text>
      {children}
    </View>
  );
}

/** Nhãn nhỏ phía trên một nhóm biến thể (vd. "MD · Pressed"). */
export function Caption({ children }: { children: string }) {
  const theme = useTheme();
  return <Text style={[theme.typeScale.micro, { color: theme.colors.textTertiary }]}>{children}</Text>;
}

/** Hàng ngang tự xuống dòng để xếp nhiều biến thể. */
export function Row({ children }: { children: React.ReactNode }) {
  const theme = useTheme();
  return <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: theme.spacing.sm }}>{children}</View>;
}
