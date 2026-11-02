import React, { useState } from 'react';
import { StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';
import { useTheme } from '../theme';

export interface InputProps extends Omit<TextInputProps, 'style'> {
  label?: string;
  errorMessage?: string;
  disabled?: boolean;
}

/**
 * Input — text field có label + error message, đủ state: default / focused / error / disabled.
 * Màu viền/chữ lấy từ theme, không hardcode.
 */
export function Input({ label, errorMessage, disabled = false, onFocus, onBlur, ...rest }: InputProps) {
  const theme = useTheme();
  const [focused, setFocused] = useState(false);
  const hasError = Boolean(errorMessage);

  const borderColor = hasError
    ? theme.colors.danger
    : focused
      ? theme.colors.borderFocused
      : theme.colors.border;

  return (
    <View style={styles.container}>
      {label ? (
        <Text
          style={{
            color: theme.colors.textSecondary,
            fontSize: theme.typography.size.sm,
            marginBottom: theme.spacing.xs,
          }}
        >
          {label}
        </Text>
      ) : null}
      <TextInput
        {...rest}
        editable={!disabled}
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
          {
            borderColor,
            backgroundColor: disabled ? theme.colors.disabledBackground : theme.colors.background,
            color: disabled ? theme.colors.disabledText : theme.colors.textPrimary,
            borderRadius: theme.radius.md,
            paddingHorizontal: theme.spacing.md,
            paddingVertical: theme.spacing.sm,
            fontSize: theme.typography.size.md,
          },
        ]}
      />
      {hasError ? (
        <Text
          style={{
            color: theme.colors.danger,
            fontSize: theme.typography.size.xs,
            marginTop: theme.spacing.xs,
          }}
        >
          {errorMessage}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
  },
  input: {
    borderWidth: 1,
    minHeight: 44,
  },
});
