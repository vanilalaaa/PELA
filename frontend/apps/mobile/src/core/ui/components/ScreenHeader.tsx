import React from 'react';
import { StyleSheet, View } from 'react-native';
import { UIIcon } from '../icons';
import { IconButton } from './IconButton';

export interface ScreenHeaderProps {
  onBack: () => void;
  /** Nhãn nút quay lại (i18n). */
  backLabel: string;
  testID?: string;
}

/**
 * Hàng đầu màn có nút quay lại (Figma 02/04/05: IconButton ghost 44, UIIcon ArrowLeft 20, sát lề trái).
 * Dùng khi màn đặt headerShown: false và tự vẽ header theo Figma.
 */
export function ScreenHeader({ onBack, backLabel, testID }: ScreenHeaderProps) {
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
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', height: 44, alignItems: 'center', marginLeft: -12 },
});
