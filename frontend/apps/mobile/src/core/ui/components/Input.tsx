import React, { useState } from 'react';
import { StyleSheet, TextInput, type TextInputProps } from 'react-native';
import { useTheme } from '../theme';
import { FieldShell, type FieldState } from './FieldShell';

export interface InputProps extends Omit<TextInputProps, 'style' | 'editable'> {
  label?: string;
  /** Có lỗi thì ô chuyển Error và hiện dòng lỗi thay cho hint (UI-2). */
  errorMessage?: string;
  hint?: string;
  disabled?: boolean;
  /** Icon đầu dòng 22: nhận màu (ink-400, focused blue-600, disabled ink-400) và kích thước. */
  renderLeftIcon?: (color: string, size: number) => React.ReactNode;
  /** Phần tử cuối ô (vd. nút hiện/ẩn mật khẩu). */
  trailing?: React.ReactNode;
  /** Đệm phải của ô; PasswordField dùng 8 để chừa nút mắt. */
  paddingRight?: number;
}

const ICON_SIZE = 22;

/**
 * Ô nhập (Figma #0 Input 570:11731): nhãn Nunito 800 14, ô 52, viền 2px, giá trị Nunito 600 16,
 * hint 13/700 ink-500. Đủ trạng thái Default / Focused / Error / Disabled.
 */
export function Input({
  label,
  errorMessage,
  hint,
  disabled = false,
  renderLeftIcon,
  trailing,
  paddingRight,
  onFocus,
  onBlur,
  accessibilityLabel,
  ...rest
}: InputProps) {
  const theme = useTheme();
  const [focused, setFocused] = useState(false);
  const state: FieldState = disabled ? 'disabled' : errorMessage ? 'error' : focused ? 'focused' : 'default';
  const iconColor = state === 'focused' ? theme.componentColors.field.iconFocused : theme.componentColors.field.icon;

  return (
    <FieldShell label={label} state={state} hint={hint} errorMessage={errorMessage} paddingRight={paddingRight}>
      {renderLeftIcon ? renderLeftIcon(iconColor, ICON_SIZE) : null}
      <TextInput
        {...rest}
        editable={!disabled}
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityState={{ disabled }}
        onFocus={(e) => {
          setFocused(true);
          onFocus?.(e);
        }}
        onBlur={(e) => {
          setFocused(false);
          onBlur?.(e);
        }}
        placeholderTextColor={theme.colors.textDisabled}
        style={[
          styles.input,
          theme.typeScale.inputValue,
          { color: disabled ? theme.colors.textDisabled : theme.colors.textPrimary },
        ]}
      />
      {trailing}
    </FieldShell>
  );
}

const styles = StyleSheet.create({
  // Bỏ padding mặc định của TextInput trên Android để chữ căn giữa ô 52 như Figma.
  input: { flex: 1, paddingVertical: 0, paddingHorizontal: 0 },
});
