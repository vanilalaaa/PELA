import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { UIIcon } from '../icons';

export interface CheckboxProps {
  checked: boolean;
  onChange?: (checked: boolean) => void;
  label?: string;
  disabled?: boolean;
  /** Bắt buộc khi không có `label`. */
  accessibilityLabel?: string;
  testID?: string;
}

/**
 * Checkbox (Figma 476:5500): ô 26, bo radius/checkbox 8. Checked = blue-500 + dấu check trắng;
 * chưa chọn = trắng, viền 2px ink-300. Disabled: ô mờ 50%, nhãn ink-400. Vùng chạm ≥ 44.
 */
export function Checkbox({ checked, onChange, label, disabled = false, accessibilityLabel, testID }: CheckboxProps) {
  const theme = useTheme();
  const control = theme.componentColors.control;

  return (
    <Pressable
      testID={testID}
      onPress={disabled ? undefined : () => onChange?.(!checked)}
      disabled={disabled}
      accessibilityRole="checkbox"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ checked, disabled }}
      style={[styles.row, { minHeight: theme.spacing.tapMin }]}
    >
      <View
        style={[
          styles.box,
          { borderRadius: theme.radii.checkbox },
          checked
            ? { backgroundColor: control.on }
            : { backgroundColor: theme.colors.bgSurface, borderWidth: 2, borderColor: control.offBorder },
          disabled && styles.dim,
        ]}
      >
        {checked ? <UIIcon name="Check" size={16} color={theme.colors.textOnPrimary} /> : null}
      </View>
      {label ? (
        <Text style={[theme.typeScale.bodyBold, { color: disabled ? theme.colors.textDisabled : theme.colors.textPrimary }]}>
          {label}
        </Text>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, alignSelf: 'flex-start' },
  box: { width: 26, height: 26, alignItems: 'center', justifyContent: 'center' },
  dim: { opacity: 0.5 },
});
