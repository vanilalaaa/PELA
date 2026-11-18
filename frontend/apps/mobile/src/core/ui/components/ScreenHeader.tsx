import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { UIIcon } from '../icons';
import { IconButton } from './IconButton';

export interface ScreenHeaderProps {
  onBack: () => void;
  /** Nhãn nút quay lại (i18n). */
  backLabel: string;
  /** Tiêu đề h2 cạnh nút quay lại (Figma 44c/44d). */
  title?: string;
  testID?: string;
}

/**
 * Hàng đầu màn có nút quay lại (Figma 02/04/05: IconButton ghost 44, UIIcon ArrowLeft 20, sát lề trái).
 * Dùng khi màn đặt headerShown: false và tự vẽ header theo Figma.
 */
export function ScreenHeader({ onBack, backLabel, title, testID }: ScreenHeaderProps) {
  const theme = useTheme();
  return (
    <View style={styles.row}>
      <IconButton
        testID={testID}
        variant="ghost"
        size="md"
        accessibilityLabel={backLabel}
        onPress={onBack}
        renderIcon={(color, size) => <UIIcon name="ArrowLeft" size={size} color={color} />}
      />
      {title ? (
        <Text style={[theme.typeScale.h2, styles.title, { color: theme.colors.textPrimary }]} accessibilityRole="header">
          {title}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', minHeight: 44, alignItems: 'center', gap: 6, marginLeft: -12 },
  title: { flex: 1 },
});
