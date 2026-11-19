import React, { useRef } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, InlineAlert, OtpInput, Screen, ScreenHeader, type OtpInputHandle } from '../../../core/ui/components';
import { maskEmail } from '../../../core/ui/logic/maskEmail';
import { useTheme } from '../../../core/ui/theme';
import type { AuthStackParamList } from '../../../app/navigation/types';
import { AuthAlert } from './AuthAlert';
import { OfflineNotice } from './OfflineNotice';
import { resetDeadline } from './authForms';
import { useOtpViewModel } from './useOtpViewModel';

/**
 * 44c Nhập mã xác thực / Mã sai (Figma 669:28559, 669:28601). Không ghi thời hạn mã (Figma ghi 10 phút trái
 * SRS 5 phút; thời hạn là MNT-3) cho tới khi có API-20. ← về 44b, giữ email đã nhập.
 */
export function ForgotPasswordOtpScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const { params } = useRoute<RouteProp<AuthStackParamList, 'ForgotPasswordOtp'>>();
  const otpRef = useRef<OtpInputHandle>(null);
  const vm = useOtpViewModel(params.email, (ticket) =>
    nav.navigate('ResetPassword', {
      resetToken: ticket.resetToken,
      email: params.email,
      expiresAt: resetDeadline(Date.now(), ticket.expiresInSec),
    }),
  );
  const masked = maskEmail(params.email);

  return (
    <Screen
      contentStyle={{ gap: 14 }}
      footerWithKeyboard
      footer={
          <Button
            label={t('auth.otp.submit')}
            size="lg"
            fullWidth
            disabled={!vm.canSubmit}
            loading={vm.verifying}
            onPress={() => void vm.verify()}
            testID="otp-submit"
          />
      }
    >
      <ScreenHeader backLabel={t('auth.back')} title={t('auth.otp.title')} onBack={() => nav.goBack()} testID="otp-back" />
      <Text style={[theme.typeScale.body, { color: theme.colors.textSecondary }]}>
        {t('auth.otp.sentTo')}
        <Text style={[theme.typeScale.bodyStrong, { color: theme.colors.textPrimary }]}>{masked}</Text>
        {t('auth.otp.sentToEnd')}
      </Text>
      <OfflineNotice />
      <OtpInput
        ref={otpRef}
        value={vm.code}
        onChange={vm.change}
        onComplete={(code) => void vm.verify(code)}
        status={vm.status}
        autoFocus
        testID="otp-input"
      />
      {vm.alert ? <AuthAlert alert={vm.alert} /> : null}
      {vm.resent ? <InlineAlert tone="info" message={t('auth.otp.resent')} testID="otp-resent" /> : null}
      <Pressable
        onPress={() => void vm.resend(() => otpRef.current?.focus())}
        disabled={vm.resending}
        accessibilityRole="button"
        accessibilityState={{ disabled: vm.resending, busy: vm.resending }}
        hitSlop={{ top: 11, bottom: 11 }}
        style={styles.resend}
        testID="otp-resend"
      >
        <Text style={[theme.typeScale.bodyStrong, { color: vm.resending ? theme.colors.textTertiary : theme.colors.textLink }]}>
          {vm.resending ? t('auth.otp.resending') : t('auth.otp.resend')}
        </Text>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  resend: { alignSelf: 'flex-start' },
});
