import React from 'react';
import { Text, View } from 'react-native';
import { Controller } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import {
  DividerText,
  Input,
  PasswordField,
  PasswordStrength,
  Screen,
  ScreenHeader,
  SocialButton,
  Button,
} from '../../../core/ui/components';
import { PelaIcon, UIIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import type { AuthStackParamList } from '../../../app/navigation/types';
import { AuthAlert } from './AuthAlert';
import { AuthFooterLink } from './AuthFooterLink';
import { OfflineNotice } from './OfflineNotice';
import { FULL_NAME_MAX, EMAIL_MAX, PASSWORD_MAX } from './authForms';
import { useRegisterViewModel } from './useRegisterViewModel';

/** 02 Register / 03 Register Error (Figma 612:14374, 612:14505). */
export function RegisterScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const { params } = useRoute<RouteProp<AuthStackParamList, 'Register'>>();
  const vm = useRegisterViewModel((email, fullName) => nav.navigate('VerifyEmail', { email, fullName }), params?.fullName);
  const msg = (client?: string, server?: string) => {
    const key = client ?? server;
    return key ? t(`auth.validation.${key}`) : undefined;
  };
  const goLogin = () => nav.replace('Login');

  return (
    <Screen contentStyle={{ gap: 14 }}>
      <ScreenHeader backLabel={t('auth.back')} onBack={() => nav.goBack()} testID="register-back" />
      <Text style={[theme.typeScale.h1, { color: theme.colors.textPrimary }]} accessibilityRole="header">
        {t('auth.registerScreen.title')}
      </Text>
      <OfflineNotice />
      {vm.alert ? <AuthAlert alert={vm.alert} onLogin={goLogin} /> : null}
      <Controller
        control={vm.control}
        name="fullName"
        render={({ field, fieldState }) => (
          <Input
            ref={field.ref}
            returnKeyType="next"
            submitBehavior="submit"
            onSubmitEditing={() => vm.focusField('email')}
            testID="register-name"
            label={t('auth.fullName')}
            placeholder={t('auth.registerScreen.namePlaceholder')}
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            maxLength={FULL_NAME_MAX}
            autoComplete="name"
            textContentType="name"
            errorMessage={msg(fieldState.error?.message, vm.serverFieldErrors.fullName)}
            renderLeftIcon={(_c, size) => <PelaIcon name="Profile" size={size * 0.83} state="inactive" />}
          />
        )}
      />
      <Controller
        control={vm.control}
        name="email"
        render={({ field, fieldState }) => (
          <Input
            ref={field.ref}
            returnKeyType="next"
            submitBehavior="submit"
            onSubmitEditing={() => vm.focusField('password')}
            testID="register-email"
            label={t('auth.email')}
            placeholder={t('auth.emailPlaceholder')}
            value={field.value}
            onChangeText={(v) => {
              field.onChange(v);
              vm.clearServerError('email');
            }}
            onBlur={field.onBlur}
            maxLength={EMAIL_MAX}
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            textContentType="emailAddress"
            errorMessage={msg(fieldState.error?.message, vm.serverFieldErrors.email)}
            renderLeftIcon={(color, size) => <UIIcon name="Mail" size={size} color={color} />}
          />
        )}
      />
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
              testID="register-password"
              label={t('auth.password')}
              placeholder={t('auth.registerScreen.passwordPlaceholder')}
              value={field.value}
              onChangeText={field.onChange}
              onBlur={field.onBlur}
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
            testID="register-confirm"
            label={t('auth.confirmPassword')}
            placeholder={t('auth.registerScreen.confirmPlaceholder')}
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            maxLength={PASSWORD_MAX}
            autoComplete="new-password"
            textContentType="newPassword"
            errorMessage={msg(fieldState.error?.message, vm.serverFieldErrors.confirmPassword)}
          />
        )}
      />
      <Button
        label={t('auth.registerScreen.submit')}
        size="lg"
        fullWidth
        loading={vm.submitting}
        onPress={() => void vm.submit()}
        testID="register-submit"
      />
      <DividerText label={t('auth.orContinueWith')} />
      <View style={{ flexDirection: 'row' }}>
        <SocialButton provider="google" label="Google" disabled accessibilityLabel={t('auth.googleUnavailable')} />
      </View>
      <AuthFooterLink prompt={t('auth.registerScreen.haveAccount')} action={t('auth.login')} onPress={goLogin} testID="register-to-login" />
    </Screen>
  );
}
