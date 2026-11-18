import React from 'react';
import { ScrollView, View, type StyleProp, type ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../theme';

export type ScreenEdge = 'top' | 'bottom';

export interface ScreenProps {
  children: React.ReactNode;
  /** Cuộn nội dung (mặc định true). */
  scroll?: boolean;
  /**
   * Cạnh cần chừa safe area. Mặc định cả trên và dưới. Màn có header của navigator thì bỏ 'top'
   * (header đã chừa); màn trong MainTabs bỏ 'bottom' (BottomNav đã chừa).
   */
  edges?: readonly ScreenEdge[];
  /** Lề ngang theo spacing/screen-gutter (mặc định true). */
  padded?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
  testID?: string;
}

/**
 * Khung màn chung: nền bgCanvas, chừa safe area trên/dưới (tai thỏ, thanh điều hướng Android),
 * tuỳ chọn cuộn. Mọi màn dùng khung này thay vì tự xử lý inset.
 */
export function Screen({ children, scroll = true, edges = ['top', 'bottom'], padded = true, contentStyle, testID }: ScreenProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const inner: StyleProp<ViewStyle> = [
    {
      paddingTop: (edges.includes('top') ? insets.top : 0) + theme.spacing.md,
      paddingBottom: (edges.includes('bottom') ? insets.bottom : 0) + theme.spacing.lg,
      paddingHorizontal: padded ? theme.spacing.screenGutter : 0,
      gap: theme.spacing.lg,
    },
    contentStyle,
  ];

  if (!scroll) {
    return (
      <View testID={testID} style={[{ flex: 1, backgroundColor: theme.colors.bgCanvas }, inner]}>
        {children}
      </View>
    );
  }
  return (
    <ScrollView
      testID={testID}
      style={{ flex: 1, backgroundColor: theme.colors.bgCanvas }}
      contentContainerStyle={[{ flexGrow: 1 }, inner]}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  );
}
