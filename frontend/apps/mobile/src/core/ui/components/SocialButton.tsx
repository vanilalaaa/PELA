import React from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { SvgXml } from 'react-native-svg';
import { useTheme } from '../theme';
import { googleLogo } from '../brand/brandSvgs';
import { usePressScale } from './usePressScale';

export type SocialProvider = 'google';

export interface SocialButtonProps {
  provider: SocialProvider;
  label: string;
  onPress?: () => void;
  disabled?: boolean;
  /** Nhãn đọc màn hình (vd. "Đăng nhập bằng Google, chưa hỗ trợ"). */
  accessibilityLabel?: string;
  testID?: string;
}

const LOGOS = { google: googleLogo } as const;

/**
 * Nút đăng nhập mạng xã hội (Figma SocialButton 643:13988): pill cao 56, nền trắng, viền 2px blue-200,
 * logo 20 + chữ Nunito Bold 16 ink-900. Disabled mờ 50% (Figma không có Disabled — GAP-25).
 * Chỉ có Google (CO-6); Apple không dùng (GAP-8).
 */
export function SocialButton({ provider, label, onPress, disabled = false, accessibilityLabel, testID }: SocialButtonProps) {
  const theme = useTheme();
  const press = usePressScale(theme.motion.pressScale.button);
  const c = theme.componentColors.socialButton;
  const logo = LOGOS[provider];
  return (
    <Animated.View style={[styles.wrap, press.scaleStyle]}>
      <Pressable
        testID={testID}
        onPress={disabled ? undefined : onPress}
        onPressIn={disabled ? undefined : press.onPressIn}
        onPressOut={disabled ? undefined : press.onPressOut}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityState={{ disabled }}
        style={[
          styles.button,
          { borderRadius: theme.radii.full, backgroundColor: c.bg, borderColor: c.border },
          disabled && styles.disabled,
        ]}
      >
        <View style={styles.logo}>
          <SvgXml xml={logo.xml} width={20} height={20} />
        </View>
        <Text style={[theme.typeScale.bodyBold, styles.label, { color: c.fg }]}>{label}</Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1 },
  button: { height: 56, borderWidth: 2, paddingHorizontal: 20, gap: 10, flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  logo: { width: 20, height: 20 },
  label: { fontSize: 16 },
  disabled: { opacity: 0.5 },
});
