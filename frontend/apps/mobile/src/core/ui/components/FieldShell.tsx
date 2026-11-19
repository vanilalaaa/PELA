import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { PelaIcon } from '../icons';

export type FieldState = 'default' | 'focused' | 'error' | 'disabled';

export interface FieldShellProps {
  label?: string;
  /** Input/PasswordField dùng nhãn 14/20 ExtraBold; Select dùng body-strong 15/22 (theo Figma). */
  labelVariant?: 'input' | 'select';
  state: FieldState;
  hint?: string;
  errorMessage?: string;
  /** Khoảng đệm phải của ô (PasswordField dùng 8 để chừa nút mắt). */
  paddingRight?: number;
  children: React.ReactNode;
}

/**
 * Khung ô nhập dùng chung (Figma Input 570:11731, PasswordField 575:11919, Select 476:5481):
 * nhãn trên, ô cao 52, bo radius/input 16, viền 2px; Focused = viền blue-500 + ring-focus;
 * Error = viền coral-500 + vòng coral 22% + hint coral-700 có icon cảnh báo; Disabled = nền ink-100.
 */
export function FieldShell({ label, labelVariant = 'input', state, hint, errorMessage, paddingRight, children }: FieldShellProps) {
  const theme = useTheme();
  const borderColor =
    state === 'error' ? theme.colors.borderError : state === 'focused' ? theme.colors.borderFocus : theme.colors.borderDefault;
  const ring = state === 'focused' ? theme.rings.focus : state === 'error' ? theme.rings.error : null;
  const radius = theme.radii.input;

  return (
    <View style={styles.container}>
      {label ? (
        <Text
          style={[
            labelVariant === 'select' ? theme.typeScale.bodyStrong : theme.typeScale.inputLabel,
            { color: theme.colors.textPrimary },
          ]}
        >
          {label}
        </Text>
      ) : null}
      <View>
        {ring ? (
          <View
            pointerEvents="none"
            style={[
              {
                position: 'absolute',
                top: -ring.width,
                left: -ring.width,
                right: -ring.width,
                bottom: -ring.width,
                borderWidth: ring.width,
                borderColor: ring.color,
                borderRadius: radius + ring.width,
              },
            ]}
          />
        ) : null}
        <View
          style={[
            styles.field,
            {
              borderColor,
              borderRadius: radius,
              backgroundColor:
                state === 'disabled' ? theme.componentColors.field.bgDisabled : theme.componentColors.field.bg,
              paddingLeft: theme.spacing.md,
              paddingRight: paddingRight ?? theme.spacing.md,
            },
          ]}
        >
          {children}
        </View>
      </View>
      {state === 'error' && errorMessage ? (
        <View style={styles.hintRow}>
          <PelaIcon name="Warning" size={14} />
          <Text style={[theme.typeScale.captionBold, { color: theme.colors.warnFg }]}>{errorMessage}</Text>
        </View>
      ) : hint ? (
        <Text style={[theme.typeScale.captionBold, { color: theme.colors.textTertiary }]}>{hint}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { width: '100%', gap: 6 },
  field: {
    height: 52,
    borderWidth: 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  hintRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
});
