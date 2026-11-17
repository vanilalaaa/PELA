import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Controller } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import {
  Button,
  Checkbox,
  DividerText,
  Input,
  PasswordField,
  Screen,
  ScreenHeader,
  SocialButton,
  Tooltip,
} from '../../../core/ui/components';
import { PelaIcon, UIIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import type { AuthStackParamList } from '../../../app/navigation/types';
import { AuthAlert } from './AuthAlert';
import { AuthFooterLink } from './AuthFooterLink';
import { OfflineNotice } from './OfflineNotice';
import { useLoginViewModel } from './useLoginViewModel';

/** 05 Login / 06 Login Error / 05 Tooltip (Figma 613:14609, 613:14725, 644:17884); lỗi khác theo GAP-6. */
export function LoginScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const vm = useLoginViewModel();
  const fieldError = (client?: string, server?: string) =>
    client ? t(`auth.validation.${client}`) : server && server !== 'highlight' ? t(`auth.validation.${server}`) : undefined;

  return (
    <Screen contentStyle={{ gap: 14 }}>
      <ScreenHeader backLabel={t('auth.back')} onBack={() => nav.goBack()} testID="login-back" />
      <Text style={[theme.typeScale.h1, { color: theme.colors.textPrimary }]} accessibilityRole="header">
        {t('auth.loginScreen.title')}
      </Text>
      <OfflineNotice />
      {vm.alert ? <AuthAlert alert={vm.alert} /> : null}
      <Controller
        control={vm.control}
        name="email"
        render={({ field, fieldState }) => (
          <Input
            ref={field.ref}
            returnKeyType="next"
            submitBehavior="submit"
            onSubmitEditing={() => vm.focusField('password')}
            testID="login-email"
            label={t('auth.email')}
            placeholder={t('auth.emailPlaceholder')}
            value={field.value}
            onChangeText={(v) => {
              field.onChange(v);
              vm.clearServerError('email');
            }}
            onBlur={field.onBlur}
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            textContentType="emailAddress"
            errorMessage={fieldError(fieldState.error?.message, vm.serverFieldErrors.email)}
            invalid={vm.serverFieldErrors.email === 'highlight'}
            renderLeftIcon={(color, size) => <UIIcon name="Mail" size={size} color={color} />}
          />
        )}
      />
      <Controller
        control={vm.control}
        name="password"
        render={({ field, fieldState }) => (
          <PasswordField
            ref={field.ref}
            returnKeyType="done"
            onSubmitEditing={() => void vm.submit()}
            testID="login-password"
            label={t('auth.password')}
            placeholder={t('auth.loginScreen.passwordPlaceholder')}
            value={field.value}
            onChangeText={(v) => {
              field.onChange(v);
              vm.clearServerError('password');
            }}
            onBlur={field.onBlur}
            autoComplete="current-password"
            textContentType="password"
            errorMessage={fieldError(fieldState.error?.message, vm.serverFieldErrors.password)}
            invalid={vm.serverFieldErrors.password === 'highlight'}
          />
        )}
      />
      {/* Hai nhóm hai đầu; cỡ chữ lớn thì "Quên mật khẩu?" xuống dòng thay vì bị cắt. */}
      <View style={styles.optionsRow}>
        <View style={styles.rememberGroup}>
          <Checkbox checked={vm.remember} onChange={vm.setRemember} label={t('auth.remember')} testID="login-remember" />
          <Tooltip text={t('auth.rememberTooltip')} accessibilityLabel={t('auth.rememberInfo')} testID="login-remember-info">
            <PelaIcon name="Info" size={20} state="inactive" />
          </Tooltip>
        </View>
        <Pressable
          onPress={() => nav.navigate('ForgotPassword')}
          accessibilityRole="link"
          hitSlop={{ top: 11, bottom: 11 }}
          testID="login-forgot"
        >
          <Text style={[theme.typeScale.bodyStrong, { color: theme.colors.textLink }]}>{t('auth.loginScreen.forgot')}</Text>
        </Pressable>
      </View>
      <Button label={t('auth.login')} size="lg" fullWidth loading={vm.submitting} onPress={() => void vm.submit()} testID="login-submit" />
      <DividerText label={t('auth.orContinueWith')} />
      <View style={styles.row}>
        <SocialButton provider="google" label="Google" disabled accessibilityLabel={t('auth.googleUnavailable')} />
      </View>
      <AuthFooterLink
        prompt={t('auth.loginScreen.noAccount')}
        action={t('auth.register')}
        onPress={() => nav.replace('Register')}
        testID="login-to-register"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  optionsRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', columnGap: 6 },
  rememberGroup: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  row: { flexDirection: 'row' },
});
