import React, { useImperativeHandle, useRef, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../theme';
import { OTP_LENGTH, otpCells, sanitizeOtp } from '../logic/otp';

const CELL_W = 48;
const CELL_H = 58;
const CELL_GAP = 12;

/** Kết quả kiểm tra mã do màn hình (use case) quyết định; component chỉ hiển thị. */
export type OtpStatus = 'idle' | 'correct' | 'wrong';

export interface OtpInputProps {
  value: string;
  onChange: (code: string) => void;
  /** Gọi khi đủ số chữ số. */
  onComplete?: (code: string) => void;
  status?: OtpStatus;
  disabled?: boolean;
  length?: number;
  autoFocus?: boolean;
  testID?: string;
  /** Cho màn đưa focus về ô đầu (vd. sau "Gửi lại mã"). */
  ref?: React.Ref<OtpInputHandle>;
}

export interface OtpInputHandle {
  focus: () => void;
}

/**
 * Ô mã xác thực (Figma #4 OtpInput 578:11815): 6 ô 48×58, gap 12, bo radius/lg 12, viền 2px.
 * Empty ink-200 · Filled blue-300 · Focused blue-500 + ring-focus · Correct mint-500 + success-bg ·
 * Wrong coral-500 + warn-bg; số PELA/h1. Một TextInput ẩn nhận cả chuỗi nên dán được cả mã.
 */
export function OtpInput({
  value,
  onChange,
  onComplete,
  status = 'idle',
  disabled = false,
  length = OTP_LENGTH,
  autoFocus = false,
  testID,
  ref,
}: OtpInputProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const inputRef = useRef<React.ComponentRef<typeof TextInput>>(null);
  const [focused, setFocused] = useState(false);
  useImperativeHandle(ref, () => ({ focus: () => inputRef.current?.focus() }), []);
  // Figma: ô 48×58, gap 12 (cả hàng 360). Màn hẹp hơn thì ô co lại theo bề ngang, giữ tỉ lệ 48:58.
  const [rowWidth, setRowWidth] = useState(0);
  const cellW = rowWidth > 0 ? Math.min(CELL_W, (rowWidth - CELL_GAP * (length - 1)) / length) : CELL_W;
  const cellH = (cellW * CELL_H) / CELL_W;
  const cells = otpCells(value, length);
  const activeIndex = Math.min(value.length, length - 1);

  const handleChange = (text: string) => {
    // Mã sai: số cũ vẫn hiện nhưng gõ/dán tiếp sẽ thay toàn bộ (như đang chọn sẵn để gõ đè). Xoá lùi thì giữ.
    const digits = sanitizeOtp(text, Number.MAX_SAFE_INTEGER);
    const typedOver = status === 'wrong' && digits.length > value.length;
    const code = typedOver ? digits.slice(value.length, value.length + length) : sanitizeOtp(text, length);
    onChange(code);
    if (code.length === length) onComplete?.(code);
  };

  const cellStyle = (index: number, digit: string) => {
    if (status === 'correct') return { borderColor: theme.colors.successAccent, backgroundColor: theme.colors.successBg };
    if (status === 'wrong') return { borderColor: theme.colors.warnAccent, backgroundColor: theme.colors.warnBg };
    if (focused && index === activeIndex && value.length < length)
      return { borderColor: theme.colors.borderFocus, backgroundColor: theme.colors.bgSurface };
    if (digit) return { borderColor: theme.componentColors.otp.borderFilled, backgroundColor: theme.colors.bgSurface };
    return { borderColor: theme.colors.borderDefault, backgroundColor: theme.colors.bgSurface };
  };

  const digitColor =
    status === 'correct' ? theme.colors.successFg : status === 'wrong' ? theme.colors.warnFg : theme.colors.textPrimary;

  return (
    // Khung ngoài rộng 100% để đo bề ngang khả dụng (không phụ thuộc kích thước ô bên trong).
    <View style={styles.measure} onLayout={(e) => setRowWidth(e.nativeEvent.layout.width)}>
    <Pressable
      testID={testID}
      onPress={() => inputRef.current?.focus()}
      disabled={disabled}
      accessibilityRole="none"
      style={[styles.row, disabled && styles.disabled]}
    >
      {cells.map((digit, index) => {
        const showRing = focused && status === 'idle' && index === activeIndex && value.length < length;
        return (
          <View key={index}>
            {showRing ? (
              <View
                pointerEvents="none"
                style={[
                  { position: 'absolute', top: -theme.rings.focus.width, left: -theme.rings.focus.width, right: -theme.rings.focus.width, bottom: -theme.rings.focus.width,
                    borderWidth: theme.rings.focus.width,
                    borderColor: theme.rings.focus.color,
                    borderRadius: theme.radii.lg + theme.rings.focus.width,
                  },
                ]}
              />
            ) : null}
            <View style={[styles.cell, { width: cellW, height: cellH, borderRadius: theme.radii.lg }, cellStyle(index, digit)]}>
              <Text style={[theme.typeScale.h1, { color: digitColor }]}>{digit}</Text>
            </View>
          </View>
        );
      })}
      <TextInput
        ref={inputRef}
        value={value}
        onChangeText={handleChange}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        editable={!disabled}
        autoFocus={autoFocus}
        keyboardType="number-pad"
        textContentType="oneTimeCode"
        autoComplete={Platform.OS === 'ios' ? 'one-time-code' : 'sms-otp'}
        caretHidden
        accessibilityLabel={t('ui.otp.label', { length })}
        style={styles.hiddenInput}
      />
    </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  measure: { width: '100%' },
  row: { flexDirection: 'row', gap: CELL_GAP, alignSelf: 'center' },
  disabled: { opacity: 0.5 },
  cell: { borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  // Ô nhập thật phủ lên các ô vẽ, trong suốt, để vẫn nhận dán/gõ và bàn phím OTP.
  hiddenInput: { ...StyleSheet.absoluteFill, opacity: 0.01, color: 'transparent' },
});
