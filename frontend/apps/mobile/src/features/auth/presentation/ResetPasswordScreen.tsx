import React, { useEffect, useState } from 'react';
import { View } from 'react-native';
import { Controller } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import {
  Button,
  Chip,
  ConfirmDialog,
  PasswordField,
  PasswordStrength,
  Screen,
  ScreenHeader,
} from '../../../core/ui/components';
import { PelaIcon, UIIcon } from '../../../core/ui/icons';
import { maskEmail } from '../../../core/ui/logic/maskEmail';
import { useTheme } from '../../../core/ui/theme';
import type { AuthStackParamList } from '../../../app/navigation/types';
import { AuthAlert } from './AuthAlert';
import { OfflineNotice } from './OfflineNotice';
import { PASSWORD_MAX } from './authForms';
import { useResetPasswordViewModel } from './useResetPasswordViewModel';

/** Cờ "được rời màn" giữ trong closure (không gây render lại). */
function createLeaveGuard() {
  let allowed = false;
  return { allow: () => void (allowed = true), allowed: () => allowed };
}

/**
 * 44d Đặt mật khẩu mới (Figma 669:28663). ← / Back Android hỏi "Huỷ đặt lại mật khẩu?" rồi về Login
 * (không về 44c: mã đã dùng). Xong thì reset stack về Login kèm thông báo, không quay lại được màn này.
 */
export function ResetPasswordScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const { params } = useRoute<RouteProp<AuthStackParamList, 'ResetPassword'>>();
  const [confirmOpen, setConfirmOpen] = useState(false);
  // Đặt true trước khi rời màn có chủ đích (xong, huỷ đã xác nhận, gửi lại mã) để không hỏi lại.
  const [leave] = useState(createLeaveGuard);

  const goLogin = (passwordReset: boolean) => {
    leave.allow();
    nav.reset({ index: 1, routes: [{ name: 'Welcome' }, { name: 'Login', params: { email: params.email, passwordReset } }] });
  };
  const vm = useResetPasswordViewModel(params.resetToken, params.expiresAt, () => goLogin(true));
  const resendCode = () => {
    leave.allow();
    nav.reset({
      index: 2,
      routes: [{ name: 'Welcome' }, { name: 'Login' }, { name: 'ForgotPassword', params: { email: params.email, source: 'login' } }],
    });
  };

  useEffect(
    () =>
      nav.addListener('beforeRemove', (e) => {
        if (leave.allowed()) return;
        e.preventDefault();
        setConfirmOpen(true);
      }),
    [nav, leave],
  );

  const msg = (client?: string, server?: string) => {
    const key = client ?? server;
    return key ? t(`auth.validation.${key}`) : undefined;
  };

  return (
    <Screen
      contentStyle={{ gap: 14 }}
      footer={
          <Button
            label={t('auth.reset.submit')}
            size="lg"
            fullWidth
            disabled={vm.expired}
            loading={vm.submitting}
            onPress={() => void vm.submit()}
            testID="reset-submit"
          />
      }
    >
      <ScreenHeader backLabel={t('auth.back')} title={t('auth.reset.title')} onBack={() => setConfirmOpen(true)} testID="reset-back" />
      <Chip
        label={t('auth.reset.verified', { email: maskEmail(params.email) })}
        tone="mint"
        size="md"
        renderIcon={() => <PelaIcon name="Success" size={16} />}
        testID="reset-verified"
      />
      <OfflineNotice />
      {vm.alert ? <AuthAlert alert={vm.alert} onResendCode={resendCode} /> : null}
      <View style={{ gap: theme.spacing.sm }}>
        <Controller
          control={vm.control}
          name="password"
          render={({ field, fieldState }) => (
            <PasswordField
              ref={field.ref}
              returnKeyType="next"
              submitBehavior="submit"
              onSubmitEditing={() => vm.focusField('confirmPassword')}
              testID="reset-password"
              label={t('auth.reset.newPassword')}
              placeholder={t('auth.reset.newPasswordPlaceholder')}
              value={field.value}
              onChangeText={(v) => {
                field.onChange(v);
                vm.clearServerError('password');
              }}
              onBlur={field.onBlur}
              disabled={vm.expired}
              maxLength={PASSWORD_MAX}
              autoComplete="new-password"
              textContentType="newPassword"
              errorMessage={msg(fieldState.error?.message, vm.serverFieldErrors.password)}
            />
          )}
        />
        <PasswordStrength password={vm.watchPassword} />
      </View>
      <Controller
        control={vm.control}
        name="confirmPassword"
        render={({ field, fieldState }) => (
          <PasswordField
            ref={field.ref}
            returnKeyType="done"
            onSubmitEditing={() => void vm.submit()}
            testID="reset-confirm"
            label={t('auth.reset.confirm')}
            placeholder={t('auth.reset.confirmPlaceholder')}
            value={field.value}
            onChangeText={(v) => {
              field.onChange(v);
              vm.clearServerError('confirmPassword');
            }}
            onBlur={field.onBlur}
            disabled={vm.expired}
            maxLength={PASSWORD_MAX}
            autoComplete="new-password"
            textContentType="newPassword"
            errorMessage={msg(fieldState.error?.message, vm.serverFieldErrors.confirmPassword)}
          />
        )}
      />
      <ConfirmDialog
        visible={confirmOpen}
        destructive
        title={t('auth.reset.cancelTitle')}
        message={t('auth.reset.cancelMessage')}
        cancelLabel={t('auth.reset.cancelKeep')}
        confirmLabel={t('auth.reset.cancelConfirm')}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          setConfirmOpen(false);
          goLogin(false);
        }}
        renderIcon={(color, size) => <UIIcon name="Close" size={size} color={color} />}
        testID="reset-cancel-dialog"
      />
    </Screen>
  );
}
