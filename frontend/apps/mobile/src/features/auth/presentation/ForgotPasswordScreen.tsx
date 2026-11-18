import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Controller } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Button, ChatBubble, Input, Mascot, Screen, ScreenHeader } from '../../../core/ui/components';
import { UIIcon } from '../../../core/ui/icons';
import { useTheme } from '../../../core/ui/theme';
import type { AuthStackParamList } from '../../../app/navigation/types';
import { AuthAlert } from './AuthAlert';
import { OfflineNotice } from './OfflineNotice';
import { EMAIL_MAX } from './authForms';
import { useForgotPasswordViewModel } from './useForgotPasswordViewModel';

/**
 * 44b Lấy lại mật khẩu (Figma 669:28481). Mở từ Login nên có ô Email (Figma vẽ cho Cài đặt: chỉ hiện email
 * đã che — nhánh `source: 'settings'` để U12). Gửi xong sang 44c.
 */
export function ForgotPasswordScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const nav = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const { params } = useRoute<RouteProp<AuthStackParamList, 'ForgotPassword'>>();
  const vm = useForgotPasswordViewModel((email) => nav.navigate('ForgotPasswordOtp', { email }), params?.email ?? '');
  const msg = (client?: string, server?: string) => {
    const key = client ?? server;
    return key ? t(`auth.validation.${key}`) : undefined;
  };

  return (
    <Screen contentStyle={{ gap: 14 }}>
      <ScreenHeader backLabel={t('auth.back')} onBack={() => nav.goBack()} testID="forgot-back" />
      <View style={styles.body}>
        <View style={styles.bubbleRow}>
          <ChatBubble message={t('auth.forgot.bubble')} />
        </View>
        <View style={styles.art}>
          <Mascot pose="comfort" />
          <UIIcon name="MailOpen" size={76} color={theme.colors.bgPrimary} />
        </View>
        <Text style={[theme.typeScale.h1, styles.center, { color: theme.colors.textPrimary }]} accessibilityRole="header">
          {t('auth.forgot.title')}
        </Text>
        <Text style={[theme.typeScale.body, styles.center, { color: theme.colors.textSecondary }]}>{t('auth.forgot.subtitle')}</Text>
      </View>
      <OfflineNotice />
      {vm.alert ? <AuthAlert alert={vm.alert} /> : null}
      <Controller
        control={vm.control}
        name="email"
        render={({ field, fieldState }) => (
          <Input
            ref={field.ref}
            returnKeyType="send"
            onSubmitEditing={() => void vm.submit()}
            testID="forgot-email"
            label={t('auth.email')}
            placeholder={t('auth.emailPlaceholder')}
            value={field.value}
            onChangeText={(v) => {
              field.onChange(v);
              vm.clearServerError();
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
      <Button label={t('auth.forgot.submit')} size="lg" fullWidth loading={vm.submitting} onPress={() => void vm.submit()} testID="forgot-submit" />
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { flex: 1, justifyContent: 'center', alignItems: 'center', gap: 10 },
  bubbleRow: { alignSelf: 'stretch', alignItems: 'flex-end', paddingRight: 28 },
  art: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'center', gap: 4 },
  center: { textAlign: 'center' },
});
