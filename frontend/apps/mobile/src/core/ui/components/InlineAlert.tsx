import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';
import { PelaIcon } from '../icons';

export type InlineAlertTone = 'warn' | 'info';

export interface InlineAlertProps {
  tone?: InlineAlertTone;
  message: string;
  /** Dòng phụ (vd. giờ mở khoá, lý do đình chỉ). */
  detail?: string;
  /** Nút nhỏ bên phải (vd. "Đăng nhập" ở 03 Register Error). */
  action?: React.ReactNode;
  testID?: string;
}

/**
 * Hộp thông báo trong màn (Figma 03/06 lỗi, 04 thông tin): bo 16, padding 12/14.
 * warn: nền warnBg, viền 2px borderError, chữ bodyStrong warnFg, icon Warning.
 * info: nền infoBg, chữ bodyBold textBrand, icon Info. Lỗi được đọc ngay khi hiện (live region).
 */
export function InlineAlert({ tone = 'warn', message, detail, action, testID }: InlineAlertProps) {
  const theme = useTheme();
  const warn = tone === 'warn';
  const fg = warn ? theme.colors.warnFg : theme.colors.textBrand;
  return (
    <View
      testID={testID}
      accessible={!action}
      accessibilityRole={warn ? 'alert' : 'text'}
      accessibilityLiveRegion={warn ? 'assertive' : 'polite'}
      accessibilityLabel={detail ? `${message} ${detail}` : message}
      style={[
        styles.box,
        { borderRadius: theme.radii.xl },
        warn
          ? { backgroundColor: theme.colors.warnBg, borderWidth: 2, borderColor: theme.colors.borderError }
          : { backgroundColor: theme.colors.infoBg },
      ]}
    >
      <PelaIcon name={warn ? 'Warning' : 'Info'} size={22} />
      <View style={styles.texts}>
        <Text style={[warn ? theme.typeScale.bodyStrong : theme.typeScale.bodyBold, { color: fg }]}>{message}</Text>
        {detail ? <Text style={[theme.typeScale.caption, { color: fg }]}>{detail}</Text> : null}
      </View>
      {action}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 12, paddingLeft: 14, paddingRight: 10, width: '100%' },
  texts: { flex: 1, gap: 2 },
});
